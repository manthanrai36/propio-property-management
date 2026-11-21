package com.propio.repository;

import com.propio.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByUserId(Long userId);

    Optional<Tenant> findByEmail(String email);

    @Query("SELECT t FROM Tenant t LEFT JOIN FETCH t.user WHERE t.id = :id")
    Optional<Tenant> findByIdWithUser(@Param("id") Long id);

    @Query(value = "SELECT t FROM Tenant t " +
                   "WHERE (:keyword IS NULL OR :keyword = '' " +
                   "   OR LOWER(t.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "   OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "   OR LOWER(t.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "   OR LOWER(t.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(t) FROM Tenant t " +
                        "WHERE (:keyword IS NULL OR :keyword = '' " +
                        "   OR LOWER(t.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(t.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(t.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Tenant> searchTenants(@Param("keyword") String keyword, Pageable pageable);
}
