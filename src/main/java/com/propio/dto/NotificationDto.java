package com.propio.dto;

import com.propio.model.Notification;
import com.propio.model.NotificationType;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {

    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private String typeDisplayName;
    private String icon;
    private String textColor;
    private String bgColor;
    private String linkUrl;
    private boolean isRead;
    private String timeAgo;
    private LocalDateTime createdAt;

    public static NotificationDto fromEntity(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .typeDisplayName(n.getType() != null ? n.getType().getDisplayName() : "Notification")
                .icon(n.getType() != null ? n.getType().getIcon() : "bi-bell-fill")
                .textColor(n.getType() != null ? n.getType().getTextColor() : "text-primary")
                .bgColor(n.getType() != null ? n.getType().getBgColor() : "bg-primary-subtle")
                .linkUrl(n.getLinkUrl())
                .isRead(n.isRead())
                .timeAgo(formatTimeAgo(n.getCreatedAt()))
                .createdAt(n.getCreatedAt())
                .build();
    }

    private static String formatTimeAgo(LocalDateTime time) {
        if (time == null) return "Just now";
        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = duration.toHours();
        if (hours < 24) return hours + "h ago";
        long days = duration.toDays();
        if (days == 1) return "Yesterday";
        if (days < 30) return days + "d ago";
        return time.toLocalDate().toString();
    }
}
