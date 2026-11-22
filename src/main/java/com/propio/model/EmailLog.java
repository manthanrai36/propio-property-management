package com.propio.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity tracking every automated email dispatched, simulated, or retried by the system.
 */
@Entity
@Table(name = "email_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient", nullable = false, length = 180)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 50)
    private EmailType emailType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EmailStatus status;

    @Lob
    @Column(name = "html_body", columnDefinition = "LONGTEXT")
    private String htmlBody;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * Entity ID (e.g., paymentId, leaseId, requestId) used for duplicate dispatch protection.
     */
    @Column(name = "reference_id")
    private Long referenceId;

    @Builder.Default
    @Column(name = "has_attachment", nullable = false)
    private boolean hasAttachment = false;

    @Column(name = "attachment_name", length = 180)
    private String attachmentName;
}
