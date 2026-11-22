package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.NotificationDto;
import com.propio.model.User;
import com.propio.service.NotificationService;
import com.propio.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.getUserById(userDetails.getId());
        // Seed default notifications if user has zero notifications
        notificationService.seedInitialNotificationsIfEmpty(user);

        long unreadCount = notificationService.getUnreadCount(user);
        List<NotificationDto> notifications = notificationService.getRecentNotifications(user, 10);

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        response.put("notifications", notifications);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.getUserById(userDetails.getId());
        boolean success = notificationService.markAsRead(id, user);
        long unreadCount = notificationService.getUnreadCount(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("unreadCount", unreadCount);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.getUserById(userDetails.getId());
        notificationService.markAllAsRead(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("unreadCount", 0);

        return ResponseEntity.ok(response);
    }
}
