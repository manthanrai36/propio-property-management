package com.propio.repository;

import com.propio.model.Lease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaseRepository extends JpaRepository<Lease, Long> {

    List<Lease> findByActiveTrue();

    long countByActiveTrue();

    List<Lease> findByTenantId(Long tenantId);

    Optional<Lease> findByTenantIdAndActiveTrue(Long tenantId);

    List<Lease> findByEndDateBetweenAndActiveTrue(LocalDate start, LocalDate end);

    List<Lease> findByEndDateAndActiveTrue(LocalDate date);

    @Query("SELECT l FROM Lease l JOIN FETCH l.unit JOIN FETCH l.tenant WHERE l.endDate BETWEEN :start AND :end AND l.active = true")
    List<Lease> findExpiringLeasesWithDetails(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
