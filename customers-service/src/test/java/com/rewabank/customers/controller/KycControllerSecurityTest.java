package com.rewabank.customers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.customers.repository.CustomerRepository;
import com.rewabank.customers.services.CustomerService;
import com.rewabank.customers.services.DocumentService;
import com.rewabank.customers.services.KycService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Negative / security tests for KycController.
 * Covers: unauthenticated access, role-based access control (RM-only
 * operations), empty file upload rejection, and malformed path variables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KycControllerSecurityTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @SuppressWarnings("deprecation") @MockBean KycService          kycService;
    @SuppressWarnings("deprecation") @MockBean DocumentService     documentService;
    @SuppressWarnings("deprecation") @MockBean CustomerService     customerService;
    @SuppressWarnings("deprecation") @MockBean CustomerRepository  customerRepository;
    @SuppressWarnings("deprecation") @MockBean S3Client            s3Client;
    @SuppressWarnings("deprecation") @MockBean S3Presigner         s3Presigner;
    @SuppressWarnings("deprecation") @MockBean KafkaTemplate<?, ?>  kafkaTemplate;
    @SuppressWarnings("deprecation") @MockBean RedisConnectionFactory redisConnectionFactory;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    // ── Rule: Unauthenticated access must be rejected ─────────────────────────

    @Test
    void getPendingKyc_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitKyc_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/kyc/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentNumber\":\"ABCDE1234F\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startReview_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/kyc/{customerId}/start-review", CUSTOMER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyKyc_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/kyc/{customerId}/verify", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDocumentUrl_Returns401_WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/documents/{documentId}/view-url", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── Rule: CUSTOMER role must not access RM-only endpoints ────────────────

    @Test
    void getPendingKyc_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/pending")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void startReview_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/kyc/{customerId}/start-review", CUSTOMER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyKyc_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(patch("/api/v1/kyc/{customerId}/verify", CUSTOMER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDocumentUrl_Returns403_WhenCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/documents/{documentId}/view-url", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDocumentUrl_Returns403_WhenTellerRole() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/documents/{documentId}/view-url", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TELLER"))))
                .andExpect(status().isForbidden());
    }

    // ── Rule: Empty / missing file must be rejected at controller level ───────

    @Test
    void uploadDocument_Returns400_WhenFileIsEmpty() throws Exception {
        mockMvc.perform(multipart("/api/v1/kyc/documents/upload")
                        .file("file", new byte[0])
                        .param("documentType", "PAN_CARD")
                        .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Invalid documentType enum must be rejected ─────────────────────

    @Test
    void uploadDocument_Returns400_WhenDocumentTypeIsInvalid() throws Exception {
        mockMvc.perform(multipart("/api/v1/kyc/documents/upload")
                        .file("file", "test content".getBytes())
                        .param("documentType", "INVALID_DOC_TYPE")
                        .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: Malformed UUID path variables must be rejected ─────────────────

    @Test
    void startReview_Returns400_WhenCustomerIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/kyc/{customerId}/start-review", "not-a-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RELATIONSHIP_MANAGER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyKyc_Returns400_WhenCustomerIdIsNotUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/kyc/{customerId}/verify", "not-a-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RELATIONSHIP_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDocumentUrl_Returns400_WhenDocumentIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/documents/{documentId}/view-url", "bad-id")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RELATIONSHIP_MANAGER"))))
                .andExpect(status().isBadRequest());
    }

    // ── Rule: RM role CAN access start-review and verify ─────────────────────

    @Test
    void getPendingKyc_Returns200_WhenRelationshipManagerRole() throws Exception {
        org.mockito.Mockito.when(
                customerRepository.findByKycStatusInAndDeletedAtIsNull(
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/kyc/pending")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RELATIONSHIP_MANAGER"))))
                .andExpect(status().isOk());
    }
}
