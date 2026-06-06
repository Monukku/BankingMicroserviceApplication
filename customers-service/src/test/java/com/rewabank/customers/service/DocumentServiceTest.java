package com.rewabank.customers.service;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.entity.KycDocument;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.repository.KycDocumentRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock MinioClient              minioClient;
    @Mock KycDocumentRepository    documentRepository;
    @Mock CustomerRepository       customerRepository;

    @InjectMocks DocumentService documentService;

    private UUID customerId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customer = Customer.builder()
                .id(customerId)
                .keycloakUserId("kc-user-1")
                .email("rewa@rewabank.com")
                .fullName("Rewa Test")
                .mobileNumber("9876543210")
                .kycStatus(Customer.KycStatus.NOT_SUBMITTED)
                .build();
    }

    private MockMultipartFile validJpeg(String name) {
        return new MockMultipartFile(
                "file", name, "image/jpeg", new byte[1024]);
    }

    private MockMultipartFile validPdf(String name) {
        return new MockMultipartFile(
                "file", name, "application/pdf", new byte[2048]);
    }

    // ── uploadDocument ────────────────────────────────────────────

    @Test
    void uploadDocument_validJpeg_savesDocumentRecord() throws Exception {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));
        KycDocument saved = KycDocument.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .documentType(KycDocument.DocumentType.AADHAAR_FRONT)
                .minioObjectKey("kyc/some/key.jpg")
                .originalFileName("aadhaar.jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(1024L)
                .build();
        when(documentRepository.save(any())).thenReturn(saved);

        KycDocument result = documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.AADHAAR_FRONT, validJpeg("aadhaar.jpg"));

        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getDocumentType()).isEqualTo(KycDocument.DocumentType.AADHAAR_FRONT);
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(documentRepository).save(any(KycDocument.class));
    }

    @Test
    void uploadDocument_validPdf_accepted() throws Exception {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.PAN_CARD, validPdf("pan.pdf")))
                .doesNotThrowAnyException();
    }

    @Test
    void uploadDocument_emptyFile_throws() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.AADHAAR_FRONT, emptyFile))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("File is empty");
    }

    @Test
    void uploadDocument_fileTooLarge_throws() {
        // 5MB + 1 byte
        byte[] bigContent = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", bigContent);

        assertThatThrownBy(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.AADHAAR_FRONT, bigFile))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadDocument_invalidContentType_throws() {
        MockMultipartFile gifFile = new MockMultipartFile(
                "file", "photo.gif", "image/gif", new byte[512]);

        assertThatThrownBy(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.SELFIE, gifFile))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void uploadDocument_customerNotFound_throws() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.uploadDocument(
                "ghost", KycDocument.DocumentType.AADHAAR_FRONT, validJpeg("a.jpg")))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void uploadDocument_minioFails_throwsUploadException() throws Exception {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO unreachable"));

        assertThatThrownBy(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.AADHAAR_FRONT, validJpeg("a.jpg")))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Document upload failed");
    }

    @Test
    void uploadDocument_noExtensionInFilename_usesBinExtension() throws Exception {
        MockMultipartFile noExt = new MockMultipartFile(
                "file", "noext", "image/jpeg", new byte[512]);
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));
        when(documentRepository.save(any())).thenAnswer(inv -> {
            KycDocument d = inv.getArgument(0);
            // object key must end with .bin when filename has no extension
            assertThat(d.getMinioObjectKey()).endsWith(".bin");
            return d;
        });

        documentService.uploadDocument("kc-user-1", KycDocument.DocumentType.SELFIE, noExt);
    }

    // ── getDocumentViewUrl ────────────────────────────────────────

    @Test
    void getDocumentViewUrl_found_returnsPresignedUrl() throws Exception {
        UUID docId = UUID.randomUUID();
        KycDocument doc = KycDocument.builder()
                .id(docId)
                .customerId(customerId)
                .minioObjectKey("kyc/path/to/doc.jpg")
                .build();
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.rewabank.local/presigned/doc.jpg?token=abc");

        String url = documentService.getDocumentViewUrl(docId);

        assertThat(url).contains("presigned");
        verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void getDocumentViewUrl_notFound_throws() {
        UUID unknownDocId = UUID.randomUUID();
        when(documentRepository.findById(unknownDocId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocumentViewUrl(unknownDocId))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    void getDocumentViewUrl_minioFails_throws() throws Exception {
        UUID docId = UUID.randomUUID();
        KycDocument doc = KycDocument.builder()
                .id(docId)
                .minioObjectKey("kyc/path/doc.pdf")
                .build();
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("MinIO timeout"));

        assertThatThrownBy(() -> documentService.getDocumentViewUrl(docId))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Failed to generate document view URL");
    }

    // ── getDocuments ──────────────────────────────────────────────

    @Test
    void getDocuments_returnsListFromRepository() {
        KycDocument d1 = KycDocument.builder().id(UUID.randomUUID()).customerId(customerId).build();
        KycDocument d2 = KycDocument.builder().id(UUID.randomUUID()).customerId(customerId).build();
        when(documentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(d1, d2));

        List<KycDocument> result = documentService.getDocuments(customerId);

        assertThat(result).hasSize(2);
        verify(documentRepository).findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Test
    void getDocuments_noDocuments_returnsEmptyList() {
        when(documentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of());

        List<KycDocument> result = documentService.getDocuments(customerId);

        assertThat(result).isEmpty();
    }
}