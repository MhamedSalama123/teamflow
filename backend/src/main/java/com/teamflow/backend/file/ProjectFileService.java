package com.teamflow.backend.file;

import com.teamflow.backend.file.dto.FileResponse;
import com.teamflow.backend.project.Project;
import com.teamflow.backend.project.ProjectNotFoundException;
import com.teamflow.backend.project.ProjectRepository;
import com.teamflow.backend.user.InvalidFileException;
import com.teamflow.backend.user.User;
import com.teamflow.backend.workspace.WorkspaceMember;
import com.teamflow.backend.workspace.WorkspaceMembershipService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectFileService {

    /** Uploads larger than this are rejected (aligned with the multipart limit). */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipService membershipService;
    private final FileStorage fileStorage;

    /** A stored file's metadata paired with its readable content. */
    public record FileContent(ProjectFile file, Resource resource) {}

    @Transactional
    public FileResponse upload(String actorEmail, Long projectId, MultipartFile file) {
        ProjectContext context = requireProjectContext(actorEmail, projectId);
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("No file was provided.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidFileException("Files must be 10 MB or smaller.");
        }
        String contentType = resolveContentType(file);
        String storedName = fileStorage.store(file);
        ProjectFile saved = projectFileRepository.save(ProjectFile.builder()
                .project(context.project())
                .uploadedBy(context.actor())
                .originalName(sanitizeName(file.getOriginalFilename()))
                .storedName(storedName)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .build());
        return FileResponse.from(saved, isPreviewable(contentType));
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(String actorEmail, Long projectId) {
        requireProjectContext(actorEmail, projectId);
        return projectFileRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId).stream()
                .map(f -> FileResponse.from(f, isPreviewable(f.getContentType())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FileContent download(String actorEmail, Long fileId) {
        ProjectFile file = requireAccessibleFile(actorEmail, fileId);
        return new FileContent(file, fileStorage.load(file.getStoredName()));
    }

    /** Like {@link #download} but only for image and PDF files, which browsers can render inline. */
    @Transactional(readOnly = true)
    public FileContent preview(String actorEmail, Long fileId) {
        ProjectFile file = requireAccessibleFile(actorEmail, fileId);
        if (!isPreviewable(file.getContentType())) {
            throw new ProjectFileNotFoundException();
        }
        return new FileContent(file, fileStorage.load(file.getStoredName()));
    }

    /** Loads a file and asserts the caller is an active member of the owning workspace. */
    private ProjectFile requireAccessibleFile(String actorEmail, Long fileId) {
        ProjectFile file = projectFileRepository.findById(fileId)
                .orElseThrow(ProjectFileNotFoundException::new);
        membershipService.requireActiveMembership(file.getProject().getWorkspace().getId(), actorEmail);
        return file;
    }

    /** The project plus the acting user, after asserting the caller is an active workspace member. */
    private record ProjectContext(Project project, User actor) {}

    private ProjectContext requireProjectContext(String actorEmail, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(ProjectNotFoundException::new);
        WorkspaceMember membership =
                membershipService.requireActiveMembership(project.getWorkspace().getId(), actorEmail);
        return new ProjectContext(project, membership.getUser());
    }

    static boolean isPreviewable(String contentType) {
        return contentType != null
                && (contentType.startsWith("image/") || contentType.equals("application/pdf"));
    }

    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return (contentType == null || contentType.isBlank()) ? DEFAULT_CONTENT_TYPE : contentType;
    }

    private static String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }
        // Strip any path components a client may have included.
        String cleaned = StringUtils.getFilename(StringUtils.cleanPath(originalName));
        return (cleaned == null || cleaned.isBlank()) ? "file" : cleaned;
    }
}
