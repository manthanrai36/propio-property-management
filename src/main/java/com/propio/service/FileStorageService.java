package com.propio.service;

import com.propio.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path rootUploadPath;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final List<String> ALLOWED_AVATAR_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2 MB

    public FileStorageService(@Value("${propio.upload.dir:uploads}") String uploadDir) {
        this.rootUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootUploadPath.resolve("properties"));
            Files.createDirectories(this.rootUploadPath.resolve("tenants"));
            Files.createDirectories(this.rootUploadPath.resolve("documents"));
            Files.createDirectories(this.rootUploadPath.resolve("maintenance"));
            log.info("Initialized file storage root at: {}", this.rootUploadPath);
        } catch (IOException ex) {
            throw new FileStorageException("Could not initialize upload storage location", ex);
        }
    }

    /**
     * Stores an uploaded image for a property and returns the public web access path.
     */
    public String storePropertyImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Validate size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException("File size exceeds 5MB limit. Please upload a smaller image.");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new FileStorageException("Invalid file type: '" + contentType + "'. Only JPG, PNG, WEBP, and GIF images are allowed.");
        }

        // Determine extension safely
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "property.jpg");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        } else {
            extension = ".jpg";
        }

        String safeFileName = UUID.randomUUID().toString() + extension;
        Path targetDir = this.rootUploadPath.resolve("properties");
        Path targetPath = targetDir.resolve(safeFileName).normalize();

        // Prevent path traversal
        if (!targetPath.startsWith(this.rootUploadPath)) {
            throw new FileStorageException("Cannot store file outside current storage directory.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved property photo: {}", safeFileName);
            return "/uploads/properties/" + safeFileName;
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store property image: " + safeFileName, ex);
        }
    }

    /**
     * Stores an uploaded avatar image for a tenant and returns the public web access path.
     * Enforces max 2 MB file size and validates jpg/png/webp types.
     */
    public String storeTenantAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Validate size (max 2 MB)
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new FileStorageException("Avatar file size exceeds 2MB limit. Please upload a smaller photo.");
        }

        // Validate MIME type (jpg, png, webp)
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new FileStorageException("Invalid image format: '" + contentType + "'. Only JPG, PNG, and WEBP formats are supported.");
        }

        // Clean filename and extract extension
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar.jpg");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        } else {
            extension = ".jpg";
        }

        String safeFileName = UUID.randomUUID().toString() + extension;
        Path targetDir = this.rootUploadPath.resolve("tenants");
        Path targetPath = targetDir.resolve(safeFileName).normalize();

        // Prevent path traversal
        if (!targetPath.startsWith(this.rootUploadPath)) {
            throw new FileStorageException("Cannot store avatar file outside designated storage directory.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved tenant avatar: {}", safeFileName);
            return "/uploads/tenants/" + safeFileName;
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store tenant avatar: " + safeFileName, ex);
        }
    }

    /**
     * Stores generated document PDF bytes in uploads/documents/ directory.
     * Returns the web-accessible URL /uploads/documents/...
     */
    public String storeDocumentBytes(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new FileStorageException("Cannot store empty document content.");
        }
        String cleanName = StringUtils.cleanPath(fileName);
        Path targetDir = this.rootUploadPath.resolve("documents");
        Path targetPath = targetDir.resolve(cleanName).normalize();

        if (!targetPath.startsWith(this.rootUploadPath)) {
            throw new FileStorageException("Cannot store document outside designated storage directory.");
        }

        try {
            Files.write(targetPath, content);
            log.info("Stored document PDF: {} ({} bytes)", cleanName, content.length);
            return "/uploads/documents/" + cleanName;
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store document file: " + cleanName, ex);
        }
    }

    /**
     * Stores an uploaded photo for a maintenance request and returns the public web access path.
     * Enforces max 5 MB file size and validates image MIME types.
     */
    public String storeMaintenancePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException("Maintenance photo exceeds 5MB limit. Please upload a smaller image.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new FileStorageException("Invalid image format: '" + contentType + "'. Only JPG, PNG, WEBP, and GIF formats are allowed.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "maintenance.jpg");
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        } else {
            extension = ".jpg";
        }

        String safeFileName = UUID.randomUUID().toString() + extension;
        Path targetDir = this.rootUploadPath.resolve("maintenance");
        Path targetPath = targetDir.resolve(safeFileName).normalize();

        if (!targetPath.startsWith(this.rootUploadPath)) {
            throw new FileStorageException("Cannot store maintenance file outside designated storage directory.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Saved maintenance photo: {}", safeFileName);
            return "/uploads/maintenance/" + safeFileName;
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store maintenance photo: " + safeFileName, ex);
        }
    }

    public Path getRootUploadPath() {
        return rootUploadPath;
    }
}
