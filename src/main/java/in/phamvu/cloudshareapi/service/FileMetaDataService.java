package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.FileMetaDataDTO;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileMetaDataService {

    private final FileMetaDataRepository fileMetaDataRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Null for anonymous callers - uploading and running PDF tools doesn't require an account,
     * only downloading a job result does (enforced by {@code SecurityConfig}, not here).
     */
    private String getCurrentUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails customUserDetails)) {
            return null;
        }
        return customUserDetails.getId();
    }

    public List<FileMetaDataDTO> uploadFiles(MultipartFile files[]) throws IOException {
        String currentUserId = getCurrentUserId();

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        List<Path> writtenPaths = new ArrayList<>();
        try {
            List<FileMetaDataDocument> pendingMetadata = new ArrayList<>();
            for (MultipartFile file : files) {
                String fileName = UUID.randomUUID() + "." + StringUtils.getFilenameExtension(file.getOriginalFilename());
                Path targetLocation = uploadPath.resolve(fileName);
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                writtenPaths.add(targetLocation);

                pendingMetadata.add(FileMetaDataDocument.builder()
                        .userId(currentUserId)
                        .fileLocation(targetLocation.toString())
                        .name(file.getOriginalFilename())
                        .size(file.getSize())
                        .type(file.getContentType())
                        .isPublic(false)
                        .uploadedAt(LocalDateTime.now())
                        .build());
            }

            return fileMetaDataRepository.saveAll(pendingMetadata).stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            deleteQuietly(writtenPaths);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new RuntimeException("Failed to upload files", e);
        }
    }

    private void deleteQuietly(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    public List<FileMetaDataDTO> getFiles(){
        String currentUserId = getCurrentUserId();
        List<FileMetaDataDocument> files = fileMetaDataRepository.findByUserIdOrderByUploadedAtDesc(currentUserId);

        return files.stream().map(item -> mapToDTO(item)).collect(Collectors.toList());

    }


    public FileMetaDataDTO getPublicFile(String fileId) {

        FileMetaDataDocument file = fileMetaDataRepository.findByIdAndIsPublic(fileId, true).orElseThrow(() -> new RuntimeException("File not found"));

        return mapToDTO(file);
    }

    public FileMetaDataDTO getDownloadableFile(String fileId) {
        String currentUserId = getCurrentUserId();
        FileMetaDataDocument file = fileMetaDataRepository.findById(fileId).orElseThrow(() -> new RuntimeException("File not found"));
        if (!Objects.equals(file.getUserId(), currentUserId) && !file.getIsPublic()) {
            throw new RuntimeException("You don't have permission to access this file");
        }
        return mapToDTO(file);
    }

    public void deleteFile(String fileId) {
        String currentUserId = getCurrentUserId();
        FileMetaDataDocument file = fileMetaDataRepository.findById(fileId).orElseThrow(() -> new RuntimeException("File not found"));
        if (!Objects.equals(file.getUserId(), currentUserId)) {
            throw new RuntimeException("You don't have permission to delete this file");
        }

        try {
            Path path = Paths.get(file.getFileLocation());
            Files.deleteIfExists(path);
            fileMetaDataRepository.deleteById(fileId);
        }catch (Exception e) {
            throw new RuntimeException("Error deleting the file");
        }
    }

    public FileMetaDataDTO togglePublic(String fileId) {
        String currentUserId = getCurrentUserId();
        FileMetaDataDocument file = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!Objects.equals(file.getUserId(), currentUserId)) {
            throw new RuntimeException("You don't have permission to toggle public status of this file");
        }
        file.setIsPublic(!file.getIsPublic());
        fileMetaDataRepository.save(file);
        return mapToDTO(file);
    }

    private FileMetaDataDTO mapToDTO(FileMetaDataDocument fileMetadataDocument) {
        return FileMetaDataDTO.builder()
                .id(fileMetadataDocument.getId())
                .fileLocation(fileMetadataDocument.getFileLocation())
                .name(fileMetadataDocument.getName())
                .size(fileMetadataDocument.getSize())
                .type(fileMetadataDocument.getType())
                .userId(fileMetadataDocument.getUserId())
                .isPublic(fileMetadataDocument.getIsPublic())
                .uploadedAt(fileMetadataDocument.getUploadedAt())
                .build();
    }

}
