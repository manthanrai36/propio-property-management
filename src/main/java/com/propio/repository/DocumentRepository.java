package com.propio.repository;

import com.propio.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByTenantId(Long tenantId);

    List<Document> findByLeaseId(Long leaseId);

    @Query("SELECT d FROM Document d " +
           "LEFT JOIN FETCH d.lease l " +
           "LEFT JOIN FETCH l.unit u " +
           "LEFT JOIN FETCH u.property p " +
           "LEFT JOIN FETCH d.tenant t " +
           "WHERE d.id = :id")
    Optional<Document> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT d FROM Document d " +
           "LEFT JOIN FETCH d.lease l " +
           "LEFT JOIN FETCH l.unit u " +
           "LEFT JOIN FETCH u.property p " +
           "LEFT JOIN FETCH d.tenant t " +
           "WHERE t.id = :tenantId " +
           "ORDER BY d.createdAt DESC")
    List<Document> findByTenantIdWithDetails(@Param("tenantId") Long tenantId);

    @Query("SELECT d FROM Document d " +
           "LEFT JOIN FETCH d.lease l " +
           "LEFT JOIN FETCH l.unit u " +
           "LEFT JOIN FETCH u.property p " +
           "LEFT JOIN FETCH d.tenant t " +
           "ORDER BY d.createdAt DESC")
    List<Document> findAllWithDetails();

    boolean existsByLeaseIdAndCategory(Long leaseId, String category);
}
