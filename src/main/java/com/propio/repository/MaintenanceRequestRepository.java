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
}
