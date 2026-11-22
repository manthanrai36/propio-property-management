package com.propio.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing an inquiry or help request submitted through the Help & Support center.
 */
@Entity
@Table(name = "support_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 50)
    private String category; // LEASING, PAYMENTS, MAINTENANCE, ACCOUNT, GENERAL

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "NEW"; // NEW, IN_PROGRESS, RESOLVED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
