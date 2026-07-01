package com.teamflow.backend.user;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded files on the local filesystem and returns the public URL under which
 * {@code WebConfig} serves them ({@code /uploads/**}). Profile photos are restricted to common image
 * types; chat attachments allow a broader set of documents and archives.
 */
@Service
public class FileStorageService {

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp");

    /** Content types accepted for chat attachments (images plus common documents/archives). */
    private static final Map<String, String> ATTACHMENT_TYPES = Map.ofEntries(
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/webp", ".webp"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("text/plain", ".txt"),
            Map.entry("application/zip", ".zip"),
            Map.entry("application/msword", ".doc"),
            Map.entry(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.ms-excel", ".xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"));

    /** Attachments larger than this are rejected. */
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload directory " + this.uploadDir, e);
        }
    }

    /**
     * Persists a profile photo under a random name and returns its public URL (e.g.
     * {@code /uploads/<id>.png}).
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("No file was provided.");
        }
        String extension = IMAGE_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidFileException("Unsupported image type. Use PNG, JPEG, GIF, or WebP.");
        }
        return write(file, extension);
    }

    /**
     * Persists a chat attachment under a random name and returns its public URL. Accepts common
     * image, document and archive types up to 10&nbsp;MB.
     */
    public String storeAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("No file was provided.");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new InvalidFileException("Attachments must be 10 MB or smaller.");
        }
        String extension = ATTACHMENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidFileException("Unsupported file type for a chat attachment.");
        }
        return write(file, extension);
    }

    private String write(MultipartFile file, String extension) {
        String filename = UUID.randomUUID() + extension;
        Path target = uploadDir.resolve(filename);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
        return "/uploads/" + filename;
    }
}
