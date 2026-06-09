package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.FileMetaDataDTO;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileMetaDataService {

    private final FileMetaDataRepository fileMetaDataRepository;
    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;

    public List<FileMetaDataDTO> uploadFiles(MultipartFile files[]) throws IOException {
       UserDocument currentProfile= profileService.getCurrenProfile();
        List<FileMetaDataDocument> savedFiles = new ArrayList<>();

        if (!userCreditsService.hasEnoughCredits(files.length)) {
            throw new RuntimeException("Not enough credits to upload files. Please purchase more credits");
        }

        Path uploadPath = Paths.get("upload").toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        for (MultipartFile file : files) {
            String fileName = UUID.randomUUID()+"."+ StringUtils.getFilenameExtension(file.getOriginalFilename());
            Path targetLocation = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            FileMetaDataDocument fileMetadata = FileMetaDataDocument.builder()
                    .fileLocation(targetLocation.toString())
                    .name(file.getOriginalFilename())
                    .size(file.getSize())
                    .type(file.getContentType())
                    .isPublic(false)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            userCreditsService.consumeCredit();

            savedFiles.add(fileMetaDataRepository.save(fileMetadata));
        }
        return savedFiles.stream().map(fileMetadataDocument -> mapToDTO(fileMetadataDocument))
                .collect(Collectors.toList());
    }

    private FileMetaDataDTO mapToDTO(FileMetaDataDocument fileMetadataDocument) {
        return FileMetaDataDTO.builder()
                .id(fileMetadataDocument.getId())
                .fileLocation(fileMetadataDocument.getFileLocation())
                .name(fileMetadataDocument.getName())
                .size(fileMetadataDocument.getSize())
                .type(fileMetadataDocument.getType())
                .clerkId(fileMetadataDocument.getClerkId())
                .isPublic(fileMetadataDocument.getIsPublic())
                .uploadedAt(fileMetadataDocument.getUploadedAt())
                .build();
    }

    public List<FileMetaDataDTO> getFiles() {
        UserDocument currentProfile = profileService.getCurrenProfile();
        List<FileMetaDataDocument> files = fileMetaDataRepository.findByClerkId(currentProfile.getId());
        return files.stream().map(this::mapToDTO).collect(Collectors.toList());
//        return files.stream().map(this::mapToDTO).toList();
    }

    @GetMapping("/public/{id}")
    public FileMetaDataDTO getPublicFile(String id) {
        Optional<FileMetaDataDocument> fileOptional = fileMetaDataRepository.findById(id);
        if (fileOptional.isEmpty() || !fileOptional.get().getIsPublic()) {
            throw new RuntimeException("Unable to get the file");
        }

        FileMetaDataDocument document = fileOptional.get();
        return mapToDTO(document);
    }

    public FileMetaDataDTO getDownloadableFile(String id) {
        FileMetaDataDocument file = fileMetaDataRepository.findById(id).orElseThrow(() -> new RuntimeException("File not found"));
        return mapToDTO(file);
    }

    public void deleteFile(String id) {
        try {
            UserDocument currentProfile = profileService.getCurrenProfile();
            FileMetaDataDocument file = fileMetaDataRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("File not found"));

            if (!file.getClerkId().equals(currentProfile.getId())) {
                throw new RuntimeException("File is not belong to current user");
            }

            Path filePath = Paths.get(file.getFileLocation());
            Files.deleteIfExists(filePath);

            fileMetaDataRepository.deleteById(id);
        }catch (Exception e) {
            throw new RuntimeException("Error deleting the file");
        }
    }

    public FileMetaDataDTO togglePublic(String id) {
        FileMetaDataDocument file = fileMetaDataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        file.setIsPublic(!file.getIsPublic());
        fileMetaDataRepository.save(file);
        return mapToDTO(file);
    }
}
