package com.propio.repository;

import com.propio.model.Unit;
import com.propio.model.UnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    long countByStatus(UnitStatus status);

    List<Unit> findByStatus(UnitStatus status);

    List<Unit> findByStatusOrderByUnitNumberAsc(UnitStatus status);

    List<Unit> findByPropertyId(Long propertyId);

    List<Unit> findByPropertyIdOrderByUnitNumberAsc(Long propertyId);

    @Query("SELECT u FROM Unit u JOIN FETCH u.property p ORDER BY p.name ASC, u.unitNumber ASC")
    List<Unit> findAllWithProperty();

    @Query("SELECT u FROM Unit u JOIN FETCH u.property p WHERE u.status = :status ORDER BY p.name ASC, u.unitNumber ASC")
    List<Unit> findByStatusWithProperty(UnitStatus status);

    @Query("SELECT u.status, COUNT(u) FROM Unit u GROUP BY u.status")
    List<Object[]> countGroupedByStatus();
}
