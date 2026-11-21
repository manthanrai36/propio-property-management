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

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    public FileStorageService(@Value("${propio.upload.dir:uploads}") String uploadDir) {
        this.rootUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootUploadPath.resolve("properties"));
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

    public Path getRootUploadPath() {
        return rootUploadPath;
    }
}
