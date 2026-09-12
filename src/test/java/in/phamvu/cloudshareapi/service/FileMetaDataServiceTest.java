package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.dto.FileMetaDataDTO;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMetaDataServiceTest {

    @Mock
    private FileMetaDataRepository fileMetaDataRepository;

    private FileMetaDataService fileMetaDataService;

    @TempDir
    Path uploadDir;

    @BeforeEach
    void setUp() {
        fileMetaDataService = new FileMetaDataService(fileMetaDataRepository);
        ReflectionTestUtils.setField(fileMetaDataService, "uploadDir", uploadDir.toString());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, "user@example.com", "pw", List.of(), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
    }

    @Test
    void uploadFiles_anonymousCaller_writesToDiskAndSavesMetadataWithNullOwner() throws IOException {
        when(fileMetaDataRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FileMetaDataDocument> docs = invocation.getArgument(0);
            docs.forEach(doc -> doc.setId("file-1"));
            return docs;
        });
        MockMultipartFile file = new MockMultipartFile("files", "report.pdf", "application/pdf", "hello".getBytes());

        List<FileMetaDataDTO> result = fileMetaDataService.uploadFiles(new org.springframework.web.multipart.MultipartFile[]{file});

        assertThat(result).hasSize(1);
        FileMetaDataDTO dto = result.get(0);
        assertThat(dto.getUserId()).isNull();
        assertThat(dto.getName()).isEqualTo("report.pdf");
        assertThat(dto.getSize()).isEqualTo(5);
        assertThat(Files.exists(Path.of(dto.getFileLocation()))).isTrue();
        assertThat(Files.readString(Path.of(dto.getFileLocation()))).isEqualTo("hello");
    }

    @Test
    void uploadFiles_authenticatedCaller_savesMetadataWithOwner() throws IOException {
        authenticateAs("user-1");
        when(fileMetaDataRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", "data".getBytes());

        List<FileMetaDataDTO> result = fileMetaDataService.uploadFiles(new org.springframework.web.multipart.MultipartFile[]{file});

        assertThat(result.get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    void getFiles_authenticatedCaller_listsOwnFilesOnly() {
        authenticateAs("user-1");
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").userId("user-1").name("a.pdf").isPublic(false).build();
        when(fileMetaDataRepository.findByUserIdOrderByUploadedAtDesc("user-1")).thenReturn(List.of(doc));

        List<FileMetaDataDTO> result = fileMetaDataService.getFiles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("file-1");
    }

    @Test
    void getFiles_anonymousCaller_looksUpByNullUserId() {
        when(fileMetaDataRepository.findByUserIdOrderByUploadedAtDesc(isNull())).thenReturn(List.of());

        List<FileMetaDataDTO> result = fileMetaDataService.getFiles();

        assertThat(result).isEmpty();
    }

    @Test
    void getPublicFile_found_returnsDto() {
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").isPublic(true).name("shared.pdf").build();
        when(fileMetaDataRepository.findByIdAndIsPublic("file-1", true)).thenReturn(Optional.of(doc));

        FileMetaDataDTO result = fileMetaDataService.getPublicFile("file-1");

        assertThat(result.getName()).isEqualTo("shared.pdf");
    }

    @Test
    void getPublicFile_notFound_throws() {
        when(fileMetaDataRepository.findByIdAndIsPublic("missing", true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileMetaDataService.getPublicFile("missing"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getDownloadableFile_owner_allowed() {
        authenticateAs("user-1");
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").userId("user-1").isPublic(false).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        FileMetaDataDTO result = fileMetaDataService.getDownloadableFile("file-1");

        assertThat(result.getId()).isEqualTo("file-1");
    }

    @Test
    void getDownloadableFile_publicFile_allowedForNonOwner() {
        authenticateAs("user-2");
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").userId("user-1").isPublic(true).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        FileMetaDataDTO result = fileMetaDataService.getDownloadableFile("file-1");

        assertThat(result.getId()).isEqualTo("file-1");
    }

    @Test
    void getDownloadableFile_privateFileNotOwner_throws() {
        authenticateAs("user-2");
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").userId("user-1").isPublic(false).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileMetaDataService.getDownloadableFile("file-1"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deleteFile_owner_deletesFromDiskAndRepository() throws IOException {
        authenticateAs("user-1");
        Path storedFile = Files.writeString(uploadDir.resolve("stored.pdf"), "content");
        FileMetaDataDocument doc = FileMetaDataDocument.builder()
                .id("file-1").userId("user-1").fileLocation(storedFile.toString()).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        fileMetaDataService.deleteFile("file-1");

        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    void deleteFile_notOwner_throwsAndLeavesFileOnDisk() throws IOException {
        authenticateAs("user-2");
        Path storedFile = Files.writeString(uploadDir.resolve("stored.pdf"), "content");
        FileMetaDataDocument doc = FileMetaDataDocument.builder()
                .id("file-1").userId("user-1").fileLocation(storedFile.toString()).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileMetaDataService.deleteFile("file-1"))
                .isInstanceOf(RuntimeException.class);
        assertThat(Files.exists(storedFile)).isTrue();
    }

    @Test
    void togglePublic_owner_flipsFlag() {
        authenticateAs("user-1");
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").userId("user-1").isPublic(false).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        FileMetaDataDTO result = fileMetaDataService.togglePublic("file-1");

        assertThat(result.isPublic()).isTrue();
    }

    @Test
    void togglePublic_notOwner_throws() {
        authenticateAs("user-2");
        FileMetaDataDocument doc = FileMetaDataDocument.builder().id("file-1").userId("user-1").isPublic(false).build();
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> fileMetaDataService.togglePublic("file-1"))
                .isInstanceOf(RuntimeException.class);
    }
}
