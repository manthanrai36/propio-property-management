package com.propio.repository;

import com.propio.model.Lease;
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
public interface LeaseRepository extends JpaRepository<Lease, Long> {

    List<Lease> findByActiveTrue();

    long countByActiveTrue();

    List<Lease> findByTenantId(Long tenantId);

    List<Lease> findByTenantIdAndActiveTrue(Long tenantId);

    @Query("SELECT l FROM Lease l " +
           "LEFT JOIN FETCH l.unit u " +
           "LEFT JOIN FETCH u.property p " +
           "LEFT JOIN FETCH l.tenant t " +
           "WHERE l.tenant.id = :tenantId AND l.active = true " +
           "ORDER BY l.endDate DESC")
    List<Lease> findActiveLeasesByTenantIdWithDetails(@Param("tenantId") Long tenantId);

    default Optional<Lease> findActiveLeaseByTenantIdWithDetails(Long tenantId) {
        List<Lease> activeLeases = findActiveLeasesByTenantIdWithDetails(tenantId);
        return activeLeases.isEmpty() ? Optional.empty() : Optional.of(activeLeases.get(0));
    }

    List<Lease> findByEndDateBetweenAndActiveTrue(LocalDate start, LocalDate end);

    List<Lease> findByEndDateAndActiveTrue(LocalDate date);

    @Query("SELECT COUNT(l) FROM Lease l WHERE l.unit.property.id = :propertyId AND l.active = true")
    long countActiveLeasesByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT COUNT(l) FROM Lease l WHERE l.unit.id = :unitId AND l.active = true")
    long countActiveLeasesByUnitId(@Param("unitId") Long unitId);

    @Query("SELECT l FROM Lease l JOIN FETCH l.unit JOIN FETCH l.tenant WHERE l.endDate BETWEEN :start AND :end AND l.active = true")
    List<Lease> findExpiringLeasesWithDetails(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT l FROM Lease l JOIN FETCH l.unit u JOIN FETCH u.property p JOIN FETCH l.tenant t JOIN FETCH t.user u2 WHERE l.active = true ORDER BY l.endDate ASC")
    List<Lease> findUpcomingExpiringLeasesWithDetails();

    @Query("SELECT COUNT(l) FROM Lease l " +
           "WHERE l.unit.id = :unitId " +
           "AND l.active = true " +
           "AND (:excludeLeaseId IS NULL OR l.id != :excludeLeaseId) " +
           "AND (l.startDate <= :endDate AND l.endDate >= :startDate)")
    long countOverlappingActiveLeases(@Param("unitId") Long unitId,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate,
                                     @Param("excludeLeaseId") Long excludeLeaseId);

    @Query("SELECT COUNT(l) FROM Lease l WHERE l.active = true AND l.endDate BETWEEN :today AND :threshold")
    long countExpiringSoon(@Param("today") LocalDate today, @Param("threshold") LocalDate threshold);

    @Query(value = "SELECT l FROM Lease l " +
                   "JOIN FETCH l.unit u " +
                   "JOIN FETCH u.property p " +
                   "JOIN FETCH l.tenant t " +
                   "WHERE (:keyword IS NULL OR :keyword = '' " +
                   "   OR LOWER(t.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "   OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "   OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "ORDER BY l.active DESC, l.endDate ASC",
           countQuery = "SELECT COUNT(l) FROM Lease l " +
                        "WHERE (:keyword IS NULL OR :keyword = '' " +
                        "   OR LOWER(l.tenant.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.tenant.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.unit.unitNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.unit.property.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Lease> searchLeasesWithDetails(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT l FROM Lease l " +
           "JOIN FETCH l.unit u " +
           "JOIN FETCH u.property p " +
           "JOIN FETCH l.tenant t " +
           "WHERE l.tenant.id = :tenantId " +
           "ORDER BY l.active DESC, l.endDate DESC")
    List<Lease> findByTenantIdWithDetails(@Param("tenantId") Long tenantId);

    @Query("SELECT l FROM Lease l " +
           "JOIN FETCH l.unit u " +
           "JOIN FETCH u.property p " +
           "JOIN FETCH l.tenant t " +
           "WHERE l.id = :id")
    Optional<Lease> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(l.monthlyRent), 0) FROM Lease l WHERE l.active = true")
    BigDecimal sumActiveMonthlyRent();
}
