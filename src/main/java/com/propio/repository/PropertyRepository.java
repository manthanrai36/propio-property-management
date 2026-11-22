package com.propio.repository;

import com.propio.model.Property;
import com.propio.model.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByCityIgnoreCase(String city);

    @Query("SELECT p FROM Property p LEFT JOIN FETCH p.units WHERE p.id = :id")
    Optional<Property> findByIdWithUnits(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Property p LEFT JOIN FETCH p.units WHERE p.id IN :ids")
    List<Property> findAllByIdWithUnits(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM Property p WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.address) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.state) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:propertyType IS NULL OR :propertyType = '' OR :propertyType = 'ALL' OR p.propertyType = :propertyType) AND " +
           "(:status IS NULL OR :status = '' OR :status = 'ALL' OR EXISTS (" +
           "   SELECT u FROM Unit u WHERE u.property = p AND u.status = :unitStatus" +
           "))")
    Page<Property> findWithFiltersAndStatus(@Param("search") String search,
                                           @Param("propertyType") String propertyType,
                                           @Param("status") String status,
                                           @Param("unitStatus") UnitStatus unitStatus,
                                           Pageable pageable);

    @Query("SELECT DISTINCT p.propertyType FROM Property p WHERE p.propertyType IS NOT NULL AND p.propertyType != ''")
    List<String> findDistinctPropertyTypes();
}
