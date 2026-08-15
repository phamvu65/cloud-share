package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.ConvertFormat;
import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.document.PdfJobType;
import in.phamvu.cloudshareapi.dto.PdfJobDTO;
import in.phamvu.cloudshareapi.dto.PdfJobResultFile;
import in.phamvu.cloudshareapi.dto.request.CompressPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.ConvertFromPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.ConvertToPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TranslatePdfRequestDTO;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfJobServiceTest {

    @Mock private PdfJobRepository pdfJobRepository;
    @Mock private FileMetaDataRepository fileMetaDataRepository;
    @Mock private PdfProcessingService pdfProcessingService;
    @Mock private DocxTranslationService docxTranslationService;
    @Mock private LibreOfficeConversionService libreOfficeConversionService;
    @Mock private PdfImageConversionService pdfImageConversionService;
    @Mock private PdfResultCleanupService pdfResultCleanupService;

    private PdfJobService pdfJobService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfJobService = new PdfJobService(pdfJobRepository, fileMetaDataRepository, pdfProcessingService,
                docxTranslationService, libreOfficeConversionService, pdfImageConversionService, pdfResultCleanupService);
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

    private FileMetaDataDocument fileOwnedBy(String userId, String name, String type) {
        return FileMetaDataDocument.builder().id("file-1").userId(userId).name(name).type(type).build();
    }

    private void stubJobSaveAssignsId() {
        when(pdfJobRepository.save(any(PdfJobDocument.class))).thenAnswer(invocation -> {
            PdfJobDocument doc = invocation.getArgument(0);
            if (doc.getId() == null) {
                doc.setId("job-1");
            }
            return doc;
        });
    }

    @Test
    void submitCompressJob_pdfFile_defaultsQualityAndDispatchesProcessing() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.pdf", "application/pdf")));
        stubJobSaveAssignsId();

        CompressPdfRequestDTO dto = CompressPdfRequestDTO.builder().fileId("file-1").build();
        PdfJobDTO result = pdfJobService.submitCompressJob(dto);

        assertThat(result.getQuality()).isEqualTo(50);
        assertThat(result.getStatus()).isEqualTo(PdfJobStatus.PENDING);
        verify(pdfProcessingService).processCompressJob("job-1", "file-1", 50);
    }

    @Test
    void submitCompressJob_qualityOutOfRange_throwsAndDoesNotDispatch() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.pdf", "application/pdf")));

        CompressPdfRequestDTO dto = CompressPdfRequestDTO.builder().fileId("file-1").quality(150).build();

        assertThatThrownBy(() -> pdfJobService.submitCompressJob(dto)).isInstanceOf(RuntimeException.class);
        verify(pdfProcessingService, never()).processCompressJob(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void submitCompressJob_nonPdfFile_throws() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));

        CompressPdfRequestDTO dto = CompressPdfRequestDTO.builder().fileId("file-1").build();

        assertThatThrownBy(() -> pdfJobService.submitCompressJob(dto)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void submitCompressJob_fileOwnedByAnotherUser_throws() {
        authenticateAs("user-2");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.pdf", "application/pdf")));

        CompressPdfRequestDTO dto = CompressPdfRequestDTO.builder().fileId("file-1").build();

        assertThatThrownBy(() -> pdfJobService.submitCompressJob(dto)).isInstanceOf(RuntimeException.class);
        verify(pdfJobRepository, never()).save(any());
    }

    @Test
    void submitTranslateJob_docxFile_defaultsSourceLanguageToAuto() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(
                fileOwnedBy("user-1", "a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
        stubJobSaveAssignsId();

        TranslatePdfRequestDTO dto = TranslatePdfRequestDTO.builder().fileId("file-1").targetLanguage("vi").build();
        PdfJobDTO result = pdfJobService.submitTranslateJob(dto);

        assertThat(result.getSourceLanguage()).isEqualTo("auto");
        assertThat(result.getTargetLanguage()).isEqualTo("vi");
        verify(docxTranslationService).processTranslateJob("job-1", "file-1", "auto", "vi");
    }

    @Test
    void submitTranslateJob_nonDocxFile_throws() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.pdf", "application/pdf")));

        TranslatePdfRequestDTO dto = TranslatePdfRequestDTO.builder().fileId("file-1").targetLanguage("vi").build();

        assertThatThrownBy(() -> pdfJobService.submitTranslateJob(dto)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void submitConvertFromPdfJob_targetPng_dispatchesToImageConversionService() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.pdf", "application/pdf")));
        stubJobSaveAssignsId();

        ConvertFromPdfRequestDTO dto = ConvertFromPdfRequestDTO.builder().fileId("file-1").targetFormat(ConvertFormat.PNG).build();
        PdfJobDTO result = pdfJobService.submitConvertFromPdfJob(dto);

        assertThat(result.getJobType()).isEqualTo(PdfJobType.PDF_TO_PNG);
        verify(pdfImageConversionService).processPdfToImageJob("job-1", "file-1", PdfJobType.PDF_TO_PNG);
        verify(libreOfficeConversionService, never()).processConversionJob(any(), any(), any());
    }

    @Test
    void submitConvertFromPdfJob_targetWord_dispatchesToLibreOfficeService() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.pdf", "application/pdf")));
        stubJobSaveAssignsId();

        ConvertFromPdfRequestDTO dto = ConvertFromPdfRequestDTO.builder().fileId("file-1").targetFormat(ConvertFormat.WORD).build();
        PdfJobDTO result = pdfJobService.submitConvertFromPdfJob(dto);

        assertThat(result.getJobType()).isEqualTo(PdfJobType.PDF_TO_WORD);
        verify(libreOfficeConversionService).processConversionJob("job-1", "file-1", PdfJobType.PDF_TO_WORD);
    }

    @Test
    void submitConvertFromPdfJob_nonPdfSource_throws() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.png", "image/png")));

        ConvertFromPdfRequestDTO dto = ConvertFromPdfRequestDTO.builder().fileId("file-1").targetFormat(ConvertFormat.WORD).build();

        assertThatThrownBy(() -> pdfJobService.submitConvertFromPdfJob(dto)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void submitConvertToPdfJob_docxSource_detectsWordToPdfAndDispatchesLibreOffice() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(
                fileOwnedBy("user-1", "a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
        stubJobSaveAssignsId();

        ConvertToPdfRequestDTO dto = ConvertToPdfRequestDTO.builder().fileId("file-1").build();
        PdfJobDTO result = pdfJobService.submitConvertToPdfJob(dto);

        assertThat(result.getJobType()).isEqualTo(PdfJobType.WORD_TO_PDF);
        verify(libreOfficeConversionService).processConversionJob("job-1", "file-1", PdfJobType.WORD_TO_PDF);
    }

    @Test
    void submitConvertToPdfJob_pngSource_detectsPngToPdfAndDispatchesImageConversion() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.png", "image/png")));
        stubJobSaveAssignsId();

        ConvertToPdfRequestDTO dto = ConvertToPdfRequestDTO.builder().fileId("file-1").build();
        PdfJobDTO result = pdfJobService.submitConvertToPdfJob(dto);

        assertThat(result.getJobType()).isEqualTo(PdfJobType.PNG_TO_PDF);
        verify(pdfImageConversionService).processImageToPdfJob("job-1", "file-1", PdfJobType.PNG_TO_PDF);
    }

    @Test
    void submitConvertToPdfJob_unsupportedFileType_throws() {
        authenticateAs("user-1");
        when(fileMetaDataRepository.findById("file-1")).thenReturn(Optional.of(fileOwnedBy("user-1", "a.zip", "application/zip")));

        ConvertToPdfRequestDTO dto = ConvertToPdfRequestDTO.builder().fileId("file-1").build();

        assertThatThrownBy(() -> pdfJobService.submitConvertToPdfJob(dto)).isInstanceOf(RuntimeException.class);
        verify(pdfJobRepository, never()).save(any());
    }

    @Test
    void getJob_ownedByCaller_returnsDto() {
        authenticateAs("user-1");
        PdfJobDocument job = PdfJobDocument.builder().id("job-1").userId("user-1").status(PdfJobStatus.PENDING).build();
        when(pdfJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        PdfJobDTO result = pdfJobService.getJob("job-1");

        assertThat(result.getId()).isEqualTo("job-1");
    }

    @Test
    void getJob_anonymousJob_visibleToAnyCaller() {
        authenticateAs("user-1");
        PdfJobDocument job = PdfJobDocument.builder().id("job-1").userId(null).status(PdfJobStatus.PENDING).build();
        when(pdfJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        PdfJobDTO result = pdfJobService.getJob("job-1");

        assertThat(result.getId()).isEqualTo("job-1");
    }

    @Test
    void getJob_ownedByAnotherUser_throwsNotFound() {
        authenticateAs("user-2");
        PdfJobDocument job = PdfJobDocument.builder().id("job-1").userId("user-1").status(PdfJobStatus.PENDING).build();
        when(pdfJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> pdfJobService.getJob("job-1")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void getJob_notFound_throws() {
        when(pdfJobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdfJobService.getJob("missing")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void downloadResult_completedJob_returnsContentAndPurgesFiles() throws IOException {
        authenticateAs("user-1");
        Path resultFile = Files.writeString(tempDir.resolve("result.pdf"), "compressed-content");
        PdfJobDocument job = PdfJobDocument.builder().id("job-1").userId("user-1")
                .status(PdfJobStatus.COMPLETED)
                .resultFilePath(resultFile.toString())
                .resultFileName("result.pdf")
                .resultContentType("application/pdf")
                .build();
        when(pdfJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        PdfJobResultFile result = pdfJobService.downloadResult("job-1");

        assertThat(new String(result.content())).isEqualTo("compressed-content");
        assertThat(result.fileName()).isEqualTo("result.pdf");
        verify(pdfResultCleanupService, times(1)).purgeJobFiles(job);
    }

    @Test
    void downloadResult_notCompleted_throwsAndDoesNotPurge() {
        authenticateAs("user-1");
        PdfJobDocument job = PdfJobDocument.builder().id("job-1").userId("user-1").status(PdfJobStatus.PROCESSING).build();
        when(pdfJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> pdfJobService.downloadResult("job-1")).isInstanceOf(RuntimeException.class);
        verify(pdfResultCleanupService, never()).purgeJobFiles(any());
    }

    @Test
    void listJobs_returnsJobsForCurrentUser() {
        authenticateAs("user-1");
        PdfJobDocument job = PdfJobDocument.builder().id("job-1").userId("user-1").status(PdfJobStatus.COMPLETED).build();
        when(pdfJobRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(job));

        List<PdfJobDTO> result = pdfJobService.listJobs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("job-1");
    }
}
