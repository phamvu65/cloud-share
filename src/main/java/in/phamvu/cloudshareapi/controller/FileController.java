package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.FileMetaDataDTO;
import in.phamvu.cloudshareapi.service.FileMetaDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j(topic = "FILE-CONTROLLER")
public class FileController {

    private final FileMetaDataService fileMetadataService;

    /**
     * API to upload a list of files to the system. Works without an account - uploading is
     * only ever a step towards running a tool (compress/translate/convert) or storing a file
     * once logged in; it never requires or costs credits.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFiles(@RequestPart("files") MultipartFile[] files) throws IOException {
        log.info("Initiating file upload API for {} files", files.length);

        List<FileMetaDataDTO> uploadedList = fileMetadataService.uploadFiles(files);

        Map<String, Object> response = new HashMap<>();
        response.put("files", uploadedList);

        log.info("Successfully uploaded {} files", files.length);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * API to retrieve all files belonging to the currently authenticated user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<FileMetaDataDTO>> getFilesForCurrentUser() {
        log.info("Initiating fetch files API for the current user");
        List<FileMetaDataDTO> list = fileMetadataService.getFiles();
        log.info("Successfully retrieved {} files for the current user", list.size());
        return ResponseEntity.ok(list);
    }

    /**
     * API to fetch metadata of a publicly shared file.
     */
    @GetMapping("/public/{id}")
    public ResponseEntity<FileMetaDataDTO> getPublicFile(@PathVariable("id") String id) {
        log.info("Initiating fetch public file metadata API for file ID: {}", id);
        FileMetaDataDTO file = fileMetadataService.getPublicFile(id);
        log.info("Successfully fetched public metadata for file ID: {}", id);
        return ResponseEntity.ok(file);
    }

    /**
     * API to download the physical file binary stream.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") String id) throws IOException {
        log.info("Initiating physical file download API for file ID: {}", id);

        FileMetaDataDTO downloadableFile = fileMetadataService.getDownloadableFile(id);
        Path path = Paths.get(downloadableFile.getFileLocation());
        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            log.error("Physical file does not exist or is unreadable at path: {}", downloadableFile.getFileLocation());
            throw new RuntimeException("Physical file not found or inaccessible on the server!");
        }

        log.info("Successfully streaming file binary for download. File name: {}", downloadableFile.getName());
        return ResponseEntity.ok()
                .contentType(resolveContentType(downloadableFile.getType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadableFile.getName() + "\"")
                .body(resource);
    }

    private MediaType resolveContentType(String storedType) {
        if (!StringUtils.hasText(storedType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(storedType);
        } catch (Exception e) {
            log.warn("Stored file type '{}' is not a valid media type, falling back to octet-stream", storedType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * API to completely delete a file from the system (Requires ownership).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable("id") String id) {
        log.info("Initiating file deletion API for file ID: {}", id);
        fileMetadataService.deleteFile(id);
        log.info("Successfully deleted file and metadata for file ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * API to toggle the public visibility status of a file.
     */
    @PatchMapping("/{id}/toggle-public")
    public ResponseEntity<FileMetaDataDTO> togglePublic(@PathVariable("id") String id) {
        log.info("Initiating toggle public status API for file ID: {}", id);
        FileMetaDataDTO file = fileMetadataService.togglePublic(id);
        log.info("Successfully toggled public status for file ID: {}. Current visibility: public={}", id, file.isPublic());
        return ResponseEntity.ok(file);
    }
}