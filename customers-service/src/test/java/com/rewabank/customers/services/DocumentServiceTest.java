package com.rewabank.customers.services;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.entity.KycDocument;
import com.rewabank.customers.exception.CustomerException;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.repository.KycDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock S3Client              s3Client;
    @Mock S3Presigner           s3Presigner;
    @Mock KycDocumentRepository documentRepository;
    @Mock CustomerRepository    customerRepository;

    @InjectMocks DocumentService documentService;

    private UUID     customerId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(documentService, "bucketName", "rewabank-kyc-docs");

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
        return new MockMultipartFile("file", name, "image/jpeg", new byte[1024]);
    }

    private MockMultipartFile validPdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", new byte[2048]);
    }

    // ── uploadDocument ────────────────────────────────────────────────────────

    @Test
    void uploadDocument_validJpeg_savesDocumentRecord() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
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
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(documentRepository).save(any(KycDocument.class));
    }

    @Test
    void uploadDocument_validPdf_accepted() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
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
    void uploadDocument_s3Fails_throwsUploadException() {
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 unreachable"));

        assertThatThrownBy(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.AADHAAR_FRONT, validJpeg("a.jpg")))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Document upload failed");
    }

    @Test
    void uploadDocument_noExtensionInFilename_throwsExtensionNotAllowed() {
        MockMultipartFile noExt = new MockMultipartFile(
                "file", "noext", "image/jpeg", new byte[512]);
        when(customerRepository.findByKeycloakUserIdAndDeletedAtIsNull("kc-user-1"))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> documentService.uploadDocument(
                "kc-user-1", KycDocument.DocumentType.SELFIE, noExt))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("File extension not allowed");
    }

    // ── getDocumentViewUrl ────────────────────────────────────────────────────

    @Test
    void getDocumentViewUrl_found_returnsPresignedUrl() throws Exception {
        UUID docId = UUID.randomUUID();
        KycDocument doc = KycDocument.builder()
                .id(docId)
                .customerId(customerId)
                .minioObjectKey("kyc/path/to/doc.jpg")
                .build();
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://b2.backblaze.com/presigned/doc.jpg?token=abc"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = documentService.getDocumentViewUrl(docId, "test-user-id");

        assertThat(url).contains("presigned");
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void getDocumentViewUrl_notFound_throws() {
        UUID unknownDocId = UUID.randomUUID();
        when(documentRepository.findById(unknownDocId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocumentViewUrl(unknownDocId, "test-user-id"))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    void getDocumentViewUrl_s3Fails_throws() {
        UUID docId = UUID.randomUUID();
        KycDocument doc = KycDocument.builder()
                .id(docId)
                .minioObjectKey("kyc/path/doc.pdf")
                .build();
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("S3 timeout"));

        assertThatThrownBy(() -> documentService.getDocumentViewUrl(docId, "test-user-id"))
                .isInstanceOf(CustomerException.class)
                .hasMessageContaining("Failed to generate document view URL");
    }

    // ── getDocuments ──────────────────────────────────────────────────────────

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