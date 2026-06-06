package com.rewabank.customers.service;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.entity.KycDocument;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.repository.KycDocumentRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final MinioClient          minioClient;
    private final KycDocumentRepository documentRepository;
    private final CustomerRepository   customerRepository;

    @Value("${minio.bucket-name:rewabank-kyc-docs}")
    private String bucketName;

    private static final long MAX_FILE_SIZE    = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "application/pdf"
    );
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".pdf"
    );

    @Transactional
    public KycDocument uploadDocument(String keycloakUserId,
                                      KycDocument.DocumentType documentType,
                                      MultipartFile file) {
        // Validate file
        if (file.isEmpty()) {
            throw new CustomerException("CUST_007", "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomerException("CUST_008", "File size exceeds 5MB limit");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new CustomerException("CUST_009", "Invalid file type. Allowed: JPEG, PNG, PDF");
        }

        Customer customer = customerRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new CustomerException("CUST_001", "Customer not found"));

        // Build MinIO object key — never expose customer ID directly in key
        String objectKey = buildObjectKey(customer.getId(), documentType, file.getOriginalFilename());

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Document uploaded to MinIO: {}", objectKey);
        } catch (Exception e) {
            log.error("MinIO upload failed: {}", e.getMessage());
            throw new CustomerException("CUST_010", "Document upload failed");
        }

        // Save document record
        KycDocument doc = KycDocument.builder()
                .customerId(customer.getId())
                .documentType(documentType)
                .minioObjectKey(objectKey)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .build();

        return documentRepository.save(doc);
    }

    // Generate pre-signed URL for RM/Auditor to view document (expires in 15 min)
    public String getDocumentViewUrl(UUID documentId, String requestedByUserId) {
        KycDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomerException("CUST_011", "Document not found"));
        log.info("KYC document access: documentId={} customerId={} requestedBy={}",
                documentId, doc.getCustomerId(), requestedByUserId);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(doc.getMinioObjectKey())
                            .method(Method.GET)
                            .expiry(15, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", e.getMessage());
            throw new CustomerException("CUST_012", "Failed to generate document view URL");
        }
    }

    public List<KycDocument> getDocuments(UUID customerId) {
        return documentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    private String buildObjectKey(UUID customerId, KycDocument.DocumentType type, String fileName) {
        // Strip path components to prevent traversal; only use the sanitized extension
        String safeName = fileName != null
                ? java.nio.file.Paths.get(fileName).getFileName().toString()
                : "upload";
        String ext = safeName.contains(".")
                ? safeName.substring(safeName.lastIndexOf('.')).toLowerCase()
                : ".bin";
        // Whitelist extension — reject anything not in allowed list
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CustomerException("CUST_013",
                    "File extension not allowed. Allowed: " + ALLOWED_EXTENSIONS);
        }
        return String.format("kyc/%s/%s/%s%s",
                customerId, type.name().toLowerCase(),
                UUID.randomUUID(), ext);
    }
}
