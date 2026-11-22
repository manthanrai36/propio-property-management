package com.propio.repository;

import com.propio.model.MaintenanceRequest;
import com.propio.model.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    List<MaintenanceRequest> findByTenantId(Long tenantId);

    List<MaintenanceRequest> findByStatus(RequestStatus status);

    long countByStatus(RequestStatus status);

    long countByStatusIn(Collection<RequestStatus> statuses);

    @Query("SELECT COALESCE(SUM(m.estimatedCost), 0) FROM MaintenanceRequest m")
    BigDecimal sumTotalEstimatedCost();

    @Query("SELECT m FROM MaintenanceRequest m " +
           "JOIN FETCH m.tenant t " +
           "LEFT JOIN FETCH t.user " +
           "JOIN FETCH m.unit u " +
           "JOIN FETCH u.property " +
           "WHERE m.status IN (com.propio.model.RequestStatus.OPEN, com.propio.model.RequestStatus.PENDING, com.propio.model.RequestStatus.IN_PROGRESS) " +
           "ORDER BY m.requestedDate DESC, m.id DESC")
    List<MaintenanceRequest> findOpenRequestsWithDetails();

    @Query("SELECT m FROM MaintenanceRequest m " +
           "JOIN FETCH m.tenant t " +
           "LEFT JOIN FETCH t.user " +
           "JOIN FETCH m.unit u " +
           "JOIN FETCH u.property p " +
           "WHERE m.id = :id")
    Optional<MaintenanceRequest> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT m FROM MaintenanceRequest m " +
           "JOIN FETCH m.tenant t " +
           "LEFT JOIN FETCH t.user " +
           "JOIN FETCH m.unit u " +
           "JOIN FETCH u.property p " +
           "WHERE t.id = :tenantId " +
           "ORDER BY m.requestedDate DESC, m.id DESC")
    List<MaintenanceRequest> findByTenantIdWithDetails(@Param("tenantId") Long tenantId);

    @Query("SELECT m FROM MaintenanceRequest m " +
           "JOIN FETCH m.tenant t " +
           "LEFT JOIN FETCH t.user " +
           "JOIN FETCH m.unit u " +
           "JOIN FETCH u.property p " +
           "ORDER BY m.requestedDate DESC, m.id DESC")
    List<MaintenanceRequest> findAllWithDetails();

    @Query(value = "SELECT m FROM MaintenanceRequest m " +
                   "JOIN FETCH m.tenant t " +
                   "LEFT JOIN FETCH t.user " +
                   "JOIN FETCH m.unit u " +
                   "JOIN FETCH u.property p " +
                   "WHERE (:status IS NULL OR m.status = :status) " +
                   "AND (:priority IS NULL OR :priority = '' OR m.priority = :priority) " +
                   "AND (:category IS NULL OR :category = '' OR m.category = :category) " +
                   "AND (:propertyId IS NULL OR p.id = :propertyId) " +
                   "AND (:search IS NULL OR :search = '' " +
                   "     OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(m.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(m.assignedTechnician) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(t.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "     OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "ORDER BY m.requestedDate DESC, m.id DESC",
           countQuery = "SELECT COUNT(m) FROM MaintenanceRequest m " +
                        "WHERE (:status IS NULL OR m.status = :status) " +
                        "AND (:priority IS NULL OR :priority = '' OR m.priority = :priority) " +
                        "AND (:category IS NULL OR :category = '' OR m.category = :category) " +
                        "AND (:propertyId IS NULL OR m.unit.property.id = :propertyId) " +
                        "AND (:search IS NULL OR :search = '' " +
                        "     OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(m.description) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(m.assignedTechnician) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(m.tenant.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(m.tenant.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(m.unit.unitNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "     OR LOWER(m.unit.property.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<MaintenanceRequest> searchRequestsWithDetails(
            @Param("status") RequestStatus status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("propertyId") Long propertyId,
            @Param("search") String search,
            Pageable pageable);
}
