package com.propio.service;

import com.propio.dto.NotificationDto;
import com.propio.model.Notification;
import com.propio.model.NotificationType;
import com.propio.model.Role;
import com.propio.model.User;
import com.propio.repository.NotificationRepository;
import com.propio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public Notification createNotification(User user, String title, String message, NotificationType type, String linkUrl) {
        if (user == null) {
            log.warn("Cannot create notification for null user");
            return null;
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .linkUrl(linkUrl)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification #{} for user '{}': [{}] {}", saved.getId(), user.getEmail(), type, title);
        return saved;
    }

    @Transactional
    public void notifyAllAdmins(String title, String message, NotificationType type, String linkUrl) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                .collect(Collectors.toList());

        for (User admin : admins) {
            createNotification(admin, title, message, type, linkUrl);
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        if (user == null || user.getId() == null) {
            return 0;
        }
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(User user, int limit) {
        if (user == null || user.getId() == null) {
            return Collections.emptyList();
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, limit))
                .stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean markAsRead(Long notificationId, User user) {
        if (user == null || user.getId() == null || notificationId == null) {
            return false;
        }
        int updated = notificationRepository.markAsRead(notificationId, user.getId());
        return updated > 0;
    }

    @Transactional
    public void markAllAsRead(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        notificationRepository.markAllAsReadForUser(user.getId());
        log.info("Marked all notifications as read for user '{}'", user.getEmail());
    }

    /**
     * Seeds realistic recent notifications for demo accounts if no notifications currently exist.
     */
    @Transactional
    public void seedInitialNotificationsIfEmpty(User user) {
        if (user == null || user.getId() == null) return;
        long count = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        if (count > 0) return;

        LocalDateTime now = LocalDateTime.now();
        if (user.getRole() == Role.ROLE_ADMIN) {
            createNotification(user, "Payment Received", "Rent payment $1,850.00 confirmed for Unit 101.", 
                    NotificationType.PAYMENT_RECEIVED, "/payments");
            createNotification(user, "Maintenance Update", "Technician assigned to Request #REQ-4 (Leaking Bathroom Pipe).", 
                    NotificationType.MAINTENANCE_UPDATE, "/maintenance");
            createNotification(user, "Lease Renewal Upcoming", "Lease for Unit 204 reaches term threshold in 60 days.", 
                    NotificationType.LEASE_EXPIRING, "/leasing");
        } else {
            createNotification(user, "Maintenance Ticket Active", "Technician assigned to your request 'Leaking Bathroom Pipe'.", 
                    NotificationType.MAINTENANCE_UPDATE, "/maintenance/my-requests");
            createNotification(user, "Payment Processed", "Your payment of $1,850.00 was confirmed. Receipt is ready.", 
                    NotificationType.PAYMENT_RECEIVED, "/payments");
            createNotification(user, "Rent Due Reminder", "Next rental cycle billing generated for upcoming month.", 
                    NotificationType.SYSTEM, "/payments");
        }
    }
}
