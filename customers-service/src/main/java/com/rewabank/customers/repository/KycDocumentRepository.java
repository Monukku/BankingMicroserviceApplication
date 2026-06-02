package com.rewabank.customers.repository;

import com.rewabank.customers.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    boolean existsByCustomerIdAndDocumentType(UUID customerId, KycDocument.DocumentType type);
}
