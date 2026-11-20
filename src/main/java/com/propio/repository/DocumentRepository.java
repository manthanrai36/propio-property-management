package com.propio.repository;

import com.propio.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByTenantId(Long tenantId);

    List<Document> findByLeaseId(Long leaseId);
}
