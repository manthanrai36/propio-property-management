package com.propio.repository;

import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByStatusAndDueDateBefore(PaymentStatus status, LocalDate date);

    List<Payment> findByDueDateBetween(LocalDate startDate, LocalDate endDate);

    List<Payment> findByLeaseTenantId(Long tenantId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.lease l WHERE l.tenant.id = :tenantId ORDER BY p.dueDate DESC")
    List<Payment> findByTenantIdWithDetails(@Param("tenantId") Long tenantId);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p JOIN FETCH p.lease l JOIN FETCH l.tenant t JOIN FETCH t.user JOIN FETCH l.unit u WHERE p.status = :status ORDER BY p.dueDate ASC")
    List<Payment> findWithDetailsByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.status = com.propio.model.PaymentStatus.PAID AND p.paidDate >= :since ORDER BY p.paidDate ASC")
    List<Payment> findPaidPaymentsSince(@Param("since") LocalDate since);

    @Query("SELECT p FROM Payment p JOIN FETCH p.lease l JOIN FETCH l.tenant t JOIN FETCH t.user JOIN FETCH l.unit u WHERE p.status IN (com.propio.model.PaymentStatus.PENDING, com.propio.model.PaymentStatus.OVERDUE) ORDER BY p.dueDate ASC")
    List<Payment> findUpcomingDuePaymentsWithDetails();
}
