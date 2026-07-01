package com.teamflow.backend.file;

import com.teamflow.backend.file.ProjectFileService.FileContent;
import com.teamflow.backend.file.dto.FileResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {

    private final ProjectFileService fileService;

    /** Uploads a file (drag &amp; drop on the client) into a project and returns its metadata + URLs. */
    @PostMapping("/files/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(
            Authentication authentication,
            @RequestParam("projectId") Long projectId,
            @RequestParam("file") MultipartFile file) {
        return fileService.upload(authentication.getName(), projectId, file);
    }

    /** Lists a project's files, newest first. */
    @GetMapping("/projects/{projectId}/files")
    public List<FileResponse> list(Authentication authentication, @PathVariable Long projectId) {
        return fileService.list(authentication.getName(), projectId);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(
            Authentication authentication, @PathVariable Long id) {
        return serve(fileService.download(authentication.getName(), id), false);
    }

    /** Serves image and PDF files inline for in-browser preview. */
    @GetMapping("/files/{id}/preview")
    public ResponseEntity<Resource> preview(
            Authentication authentication, @PathVariable Long id) {
        return serve(fileService.preview(authentication.getName(), id), true);
    }

    private ResponseEntity<Resource> serve(FileContent content, boolean inline) {
        ProjectFile file = content.file();
        ContentDisposition disposition = (inline
                        ? ContentDisposition.inline()
                        : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .body(content.resource());
    }
}
