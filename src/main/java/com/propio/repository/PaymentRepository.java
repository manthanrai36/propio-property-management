package com.propio.repository;

import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByStatusAndDueDateBefore(PaymentStatus status, LocalDate date);

    List<Payment> findByDueDateBetween(LocalDate startDate, LocalDate endDate);

    List<Payment> findByLeaseTenantId(Long tenantId);

    @Query("SELECT p FROM Payment p JOIN FETCH p.lease l JOIN FETCH l.tenant t WHERE t.id IN :tenantIds")
    List<Payment> findByTenantIdIn(@Param("tenantIds") List<Long> tenantIds);

    List<Payment> findByLeaseIdOrderByDueDateAsc(Long leaseId);

    List<Payment> findByLeaseIdOrderByDueDateDesc(Long leaseId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.lease l WHERE l.tenant.id = :tenantId ORDER BY p.dueDate DESC")
    List<Payment> findByTenantIdWithDetails(@Param("tenantId") Long tenantId);

    @Query("SELECT p FROM Payment p " +
           "JOIN FETCH p.lease l " +
           "JOIN FETCH l.unit u " +
           "JOIN FETCH u.property prop " +
           "JOIN FETCH l.tenant t " +
           "WHERE t.id = :tenantId " +
           "ORDER BY p.dueDate DESC")
    List<Payment> findAllByTenantIdWithFullDetails(@Param("tenantId") Long tenantId);

    @Query("SELECT p FROM Payment p " +
           "JOIN FETCH p.lease l " +
           "JOIN FETCH l.unit u " +
           "JOIN FETCH u.property prop " +
           "JOIN FETCH l.tenant t " +
           "WHERE p.id = :id")
    Optional<Payment> findByIdWithDetails(@Param("id") Long id);

    boolean existsByTransactionReference(String transactionReference);

    boolean existsByLeaseIdAndDueDateBetween(Long leaseId, LocalDate startDate, LocalDate endDate);

    boolean existsByLeaseIdAndDueDate(Long leaseId, LocalDate dueDate);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p")
    BigDecimal sumTotalAmount();

    @Query(value = "SELECT p FROM Payment p " +
                   "JOIN FETCH p.lease l " +
                   "JOIN FETCH l.unit u " +
                   "JOIN FETCH u.property prop " +
                   "JOIN FETCH l.tenant t " +
                   "WHERE (:status IS NULL OR p.status = :status) " +
                   "AND (:startDate IS NULL OR p.dueDate >= :startDate) " +
                   "AND (:endDate IS NULL OR p.dueDate <= :endDate) " +
                   "AND (:search IS NULL OR :search = '' " +
                   "     OR LOWER(t.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(t.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(p.transactionReference) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(prop.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "ORDER BY p.dueDate DESC",
           countQuery = "SELECT COUNT(p) FROM Payment p " +
                        "WHERE (:status IS NULL OR p.status = :status) " +
                        "AND (:startDate IS NULL OR p.dueDate >= :startDate) " +
                        "AND (:endDate IS NULL OR p.dueDate <= :endDate) " +
                        "AND (:search IS NULL OR :search = '' " +
                        "     OR LOWER(p.lease.tenant.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(p.lease.tenant.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(p.lease.tenant.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(p.transactionReference) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(p.lease.unit.unitNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(p.lease.unit.property.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Payment> searchPaymentsWithDetails(
            @Param("status") PaymentStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN FETCH p.lease l JOIN FETCH l.tenant t JOIN FETCH t.user JOIN FETCH l.unit u WHERE p.status = :status ORDER BY p.dueDate ASC")
    List<Payment> findWithDetailsByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.status = com.propio.model.PaymentStatus.PAID AND p.paidDate >= :since ORDER BY p.paidDate ASC")
    List<Payment> findPaidPaymentsSince(@Param("since") LocalDate since);

    @Query("SELECT p FROM Payment p JOIN FETCH p.lease l JOIN FETCH l.tenant t JOIN FETCH t.user JOIN FETCH l.unit u WHERE p.status IN (com.propio.model.PaymentStatus.PENDING, com.propio.model.PaymentStatus.OVERDUE) ORDER BY p.dueDate ASC")
    List<Payment> findUpcomingDuePaymentsWithDetails();
}
