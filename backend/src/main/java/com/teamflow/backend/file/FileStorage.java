package com.teamflow.backend.file;

import com.teamflow.backend.user.InvalidFileException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores project files on the local filesystem in a private directory that, unlike {@code uploads},
 * is not served statically — the bytes are only reachable through the authenticated download/preview
 * endpoints. Files are written under random names to avoid collisions and path traversal.
 */
@Service
public class FileStorage {

    private final Path storageDir;

    public FileStorage(@Value("${app.file-storage-dir:filestore}") String storageDir) {
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create file storage directory " + this.storageDir, e);
        }
    }

    /** Persists the file under a random name and returns that name. */
    public String store(MultipartFile file) {
        String extension = extensionOf(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + extension;
        Path target = storageDir.resolve(storedName).normalize();
        if (!target.startsWith(storageDir)) {
            throw new InvalidFileException("Invalid file name.");
        }
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
        return storedName;
    }

    /** Resolves a stored file to a readable resource, guarding against path traversal. */
    public Resource load(String storedName) {
        Path target = storageDir.resolve(storedName).normalize();
        if (!target.startsWith(storageDir) || !Files.isReadable(target)) {
            throw new ProjectFileNotFoundException();
        }
        return new FileSystemResource(target);
    }

    private static String extensionOf(String originalName) {
        String ext = StringUtils.getFilenameExtension(originalName);
        return (ext == null || ext.isBlank()) ? "" : "." + ext.toLowerCase();
    }
}
