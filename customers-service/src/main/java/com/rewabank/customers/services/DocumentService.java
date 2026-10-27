package com.rewabank.customers.services;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.entity.KycDocument;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final S3Client              s3Client;
    private final S3Presigner           s3Presigner;
    private final KycDocumentRepository documentRepository;
    private final CustomerRepository    customerRepository;

    @Value("${s3.bucket-name:rewabank-kyc-docs}")
    private String bucketName;

    private static final long MAX_FILE_SIZE    = 5L * 1024 * 1024; // 5MB
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

        String objectKey = buildObjectKey(customer.getId(), documentType, file.getOriginalFilename());

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            log.info("Document uploaded to S3: {}", objectKey);
        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new CustomerException("CUST_010", "Document upload failed");
        }

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
            return s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(15))
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(doc.getMinioObjectKey())
                                    .build())
                            .build()
            ).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", e.getMessage());
            throw new CustomerException("CUST_012", "Failed to generate document view URL");
        }
    }

    public List<KycDocument> getDocuments(UUID customerId) {
        return documentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    private String buildObjectKey(UUID customerId, KycDocument.DocumentType type, String fileName) {
        String safeName = fileName != null
                ? java.nio.file.Paths.get(fileName).getFileName().toString()
                : "upload";
        String ext = safeName.contains(".")
                ? safeName.substring(safeName.lastIndexOf('.')).toLowerCase()
                : ".bin";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CustomerException("CUST_013",
                    "File extension not allowed. Allowed: " + ALLOWED_EXTENSIONS);
        }
        return String.format("kyc/%s/%s/%s%s",
                customerId, type.name().toLowerCase(),
                UUID.randomUUID(), ext);
    }
}
