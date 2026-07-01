package com.teamflow.backend.file.dto;

import com.teamflow.backend.file.ProjectFile;
import com.teamflow.backend.user.User;
import java.time.Instant;

public record FileResponse(
        Long id,
        String name,
        String contentType,
        long size,
        String downloadUrl,
        String previewUrl,
        boolean previewable,
        UploaderSummary uploadedBy,
        Instant createdAt) {

    /** Minimal public view of the user who uploaded the file. */
    public record UploaderSummary(Long id, String username, String fullName) {

        static UploaderSummary from(User user) {
            return new UploaderSummary(user.getId(), user.getUsername(), user.getFullName());
        }
    }

    public static FileResponse from(ProjectFile file, boolean previewable) {
        return new FileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                "/api/files/" + file.getId() + "/download",
                previewable ? "/api/files/" + file.getId() + "/preview" : null,
                previewable,
                UploaderSummary.from(file.getUploadedBy()),
                file.getCreatedAt());
    }
}
