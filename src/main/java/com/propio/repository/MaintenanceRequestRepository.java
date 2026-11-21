package com.propio.repository;

import com.propio.model.MaintenanceRequest;
import com.propio.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    List<MaintenanceRequest> findByTenantId(Long tenantId);

    List<MaintenanceRequest> findByStatus(RequestStatus status);

    long countByStatus(RequestStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(m.estimatedCost), 0) FROM MaintenanceRequest m")
    java.math.BigDecimal sumTotalEstimatedCost();

    @org.springframework.data.jpa.repository.Query("SELECT m FROM MaintenanceRequest m JOIN FETCH m.tenant t JOIN FETCH t.user JOIN FETCH m.unit u JOIN FETCH u.property WHERE m.status IN (com.propio.model.RequestStatus.PENDING, com.propio.model.RequestStatus.IN_PROGRESS) ORDER BY m.requestedDate DESC")
    List<MaintenanceRequest> findOpenRequestsWithDetails();
}
