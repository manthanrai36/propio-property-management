package com.propio.repository;

import com.propio.model.EmailLog;
import com.propio.model.EmailStatus;
import com.propio.model.EmailType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    boolean existsByEmailTypeAndReferenceIdAndStatusIn(
            EmailType emailType, Long referenceId, Collection<EmailStatus> statuses);

    default boolean hasSentEmail(EmailType emailType, Long referenceId) {
        return existsByEmailTypeAndReferenceIdAndStatusIn(
                emailType, referenceId, List.of(EmailStatus.SENT, EmailStatus.SIMULATED_SENT));
    }

    long countByStatus(EmailStatus status);

    long countByStatusIn(Collection<EmailStatus> statuses);

    long countByCreatedAtAfter(LocalDateTime timestamp);

    @Query(value = "SELECT e FROM EmailLog e " +
                   "WHERE (:type IS NULL OR e.emailType = :type) " +
                   "AND (:status IS NULL OR e.status = :status) " +
                   "AND (:search IS NULL OR :search = '' " +
                   "     OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(e.subject) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "ORDER BY e.createdAt DESC",
           countQuery = "SELECT COUNT(e) FROM EmailLog e " +
                        "WHERE (:type IS NULL OR e.emailType = :type) " +
                        "AND (:status IS NULL OR e.status = :status) " +
                        "AND (:search IS NULL OR :search = '' " +
                        "     OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(e.subject) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<EmailLog> searchLogs(
            @Param("type") EmailType type,
            @Param("status") EmailStatus status,
            @Param("search") String search,
            Pageable pageable);
}
