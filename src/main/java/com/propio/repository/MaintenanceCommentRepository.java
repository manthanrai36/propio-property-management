package com.propio.repository;

import com.propio.model.MaintenanceComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceCommentRepository extends JpaRepository<MaintenanceComment, Long> {

    @Query("SELECT c FROM MaintenanceComment c LEFT JOIN FETCH c.author WHERE c.request.id = :requestId ORDER BY c.createdAt ASC")
    List<MaintenanceComment> findByRequestIdOrderByCreatedAtAsc(@Param("requestId") Long requestId);
}
