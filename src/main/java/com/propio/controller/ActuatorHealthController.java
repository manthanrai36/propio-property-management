package com.propio.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production-ready Actuator Health endpoint conforming to standard Spring Boot Actuator format.
 * Enables container orchestrators, cloud platforms (Render, Railway, Kubernetes), and uptime checkers
 * to poll system health, database readiness, and disk space without authentication.
 */
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
@Slf4j
public class ActuatorHealthController {

    private final DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> components = new LinkedHashMap<>();

        boolean dbHealthy = false;
        String dbProduct = "Unknown";
        try (Connection conn = dataSource.getConnection()) {
            dbHealthy = conn.isValid(2);
            dbProduct = conn.getMetaData().getDatabaseProductName();
        } catch (Exception ex) {
            log.error("Actuator health check - Database connectivity error: {}", ex.getMessage());
        }

        Map<String, Object> dbDetails = new LinkedHashMap<>();
        dbDetails.put("status", dbHealthy ? "UP" : "DOWN");
        Map<String, Object> dbInfo = new LinkedHashMap<>();
        dbInfo.put("database", dbProduct);
        dbInfo.put("validationQuery", "isValid(2)");
        dbDetails.put("details", dbInfo);
        components.put("db", dbDetails);

        // Disk space check
        File root = new File(".");
        long freeSpace = root.getFreeSpace();
        long totalSpace = root.getTotalSpace();
        Map<String, Object> diskDetails = new LinkedHashMap<>();
        boolean spaceOk = freeSpace > 10 * 1024 * 1024L; // 10MB threshold
        diskDetails.put("status", spaceOk ? "UP" : "LOW_SPACE");
        Map<String, Object> diskInfo = new LinkedHashMap<>();
        diskInfo.put("total", totalSpace);
        diskInfo.put("free", freeSpace);
        diskInfo.put("threshold", 10 * 1024 * 1024L);
        diskDetails.put("details", diskInfo);
        components.put("diskSpace", diskDetails);

        // Overall status
        String overallStatus = (dbHealthy && spaceOk) ? "UP" : "DOWN";
        response.put("status", overallStatus);
        response.put("components", components);

        return ResponseEntity
                .status((dbHealthy && spaceOk) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
