package com.propio.repository;

import com.propio.model.SupportMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findAllByOrderByCreatedAtDesc();

    Page<SupportMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SupportMessage> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(String status);
}
