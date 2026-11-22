package com.propio.service;

import com.propio.dto.SupportMessageDto;
import com.propio.model.NotificationType;
import com.propio.model.SupportMessage;
import com.propio.model.User;
import com.propio.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportService {

    private final SupportMessageRepository supportMessageRepository;
    private final NotificationService notificationService;

    @Transactional
    public SupportMessage submitMessage(SupportMessageDto dto, User user) {
        SupportMessage message = SupportMessage.builder()
                .user(user)
                .name(dto.getName().trim())
                .email(dto.getEmail().trim().toLowerCase())
                .category(dto.getCategory().trim().toUpperCase())
                .subject(dto.getSubject().trim())
                .message(dto.getMessage().trim())
                .status("NEW")
                .createdAt(LocalDateTime.now())
                .build();

        SupportMessage saved = supportMessageRepository.save(message);
        log.info("Received new support inquiry #{} [{}] from '{}' <{}>", 
                saved.getId(), saved.getCategory(), saved.getName(), saved.getEmail());

        // Notify admins of new support request
        notificationService.notifyAllAdmins(
                "New Support Inquiry: " + saved.getSubject(),
                "Inquiry from " + saved.getName() + " (" + saved.getCategory() + ")",
                NotificationType.SUPPORT,
                "/support"
        );

        // Notify user if authenticated
        if (user != null) {
            notificationService.createNotification(
                    user,
                    "Support Inquiry Received",
                    "We received your inquiry regarding '" + saved.getSubject() + "'. Our team will reply shortly.",
                    NotificationType.SUPPORT,
                    "/support"
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> getAllMessages() {
        return supportMessageRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> getMessagesForUser(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        return supportMessageRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }
}
