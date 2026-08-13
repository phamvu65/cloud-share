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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdfJobService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PPTX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final PdfProcessingService pdfProcessingService;
    private final DocxTranslationService docxTranslationService;
    private final LibreOfficeConversionService libreOfficeConversionService;
    private final PdfImageConversionService pdfImageConversionService;
    private final PdfResultCleanupService pdfResultCleanupService;

    /**
     * Null for anonymous callers - submitting/polling PDF jobs doesn't require an account,
     * only downloading a result does (enforced by {@code SecurityConfig}, not here).
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails customUserDetails)) {
            return null;
        }
        return customUserDetails.getId();
    }

    private FileMetaDataDocument getOwnedFile(String fileId, String userId) {
        FileMetaDataDocument file = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
        if (!Objects.equals(file.getUserId(), userId)) {
            throw new RuntimeException("You don't have permission to access file: " + fileId);
        }
        return file;
    }

    private boolean isPdf(FileMetaDataDocument file) {
        return "application/pdf".equalsIgnoreCase(file.getType())
                || (file.getName() != null && file.getName().toLowerCase().endsWith(".pdf"));
    }

    private boolean isDocx(FileMetaDataDocument file) {
        return DOCX_CONTENT_TYPE.equalsIgnoreCase(file.getType())
                || (file.getName() != null && file.getName().toLowerCase().endsWith(".docx"));
    }

    private boolean isPptx(FileMetaDataDocument file) {
        return PPTX_CONTENT_TYPE.equalsIgnoreCase(file.getType())
                || (file.getName() != null && file.getName().toLowerCase().endsWith(".pptx"));
    }

    private boolean isXlsx(FileMetaDataDocument file) {
        return XLSX_CONTENT_TYPE.equalsIgnoreCase(file.getType())
                || (file.getName() != null && file.getName().toLowerCase().endsWith(".xlsx"));
    }

    private boolean isHtml(FileMetaDataDocument file) {
        return (file.getType() != null && file.getType().toLowerCase().startsWith("text/html"))
                || (file.getName() != null && (file.getName().toLowerCase().endsWith(".html")
                        || file.getName().toLowerCase().endsWith(".htm")));
    }

    private boolean isPng(FileMetaDataDocument file) {
        return (file.getType() != null && file.getType().equalsIgnoreCase("image/png"))
                || (file.getName() != null && file.getName().toLowerCase().endsWith(".png"));
    }

    private boolean isJpg(FileMetaDataDocument file) {
        return (file.getType() != null && file.getType().toLowerCase().startsWith("image/jpeg"))
                || (file.getName() != null && (file.getName().toLowerCase().endsWith(".jpg")
                        || file.getName().toLowerCase().endsWith(".jpeg")));
    }

    private PdfJobType detectSourceJobType(FileMetaDataDocument file) {
        if (isDocx(file)) {
            return PdfJobType.WORD_TO_PDF;
        }
        if (isPptx(file)) {
            return PdfJobType.PPTX_TO_PDF;
        }
        if (isXlsx(file)) {
            return PdfJobType.EXCEL_TO_PDF;
        }
        if (isHtml(file)) {
            return PdfJobType.HTML_TO_PDF;
        }
        if (isPng(file)) {
            return PdfJobType.PNG_TO_PDF;
        }
        if (isJpg(file)) {
            return PdfJobType.JPG_TO_PDF;
        }
        throw new RuntimeException("Unsupported file type for conversion to PDF: " + file.getName());
    }

    private PdfJobType targetFormatToJobType(ConvertFormat targetFormat) {
        return switch (targetFormat) {
            case WORD -> PdfJobType.PDF_TO_WORD;
            case PNG -> PdfJobType.PDF_TO_PNG;
            case JPG -> PdfJobType.PDF_TO_JPG;
            case HTML -> PdfJobType.PDF_TO_HTML;
        };
    }

    private void dispatchConversionJob(String jobId, String fileId, PdfJobType jobType) {
        switch (jobType) {
            case PDF_TO_PNG, PDF_TO_JPG ->
                    pdfImageConversionService.processPdfToImageJob(jobId, fileId, jobType);
            case PNG_TO_PDF, JPG_TO_PDF ->
                    pdfImageConversionService.processImageToPdfJob(jobId, fileId, jobType);
            default -> libreOfficeConversionService.processConversionJob(jobId, fileId, jobType);
        }
    }

    public PdfJobDTO submitCompressJob(CompressPdfRequestDTO dto) {
        String userId = getCurrentUserId();
        FileMetaDataDocument file = getOwnedFile(dto.getFileId(), userId);
        if (!isPdf(file)) {
            throw new RuntimeException("Selected file is not a PDF: " + file.getName());
        }

        Integer quality = dto.getQuality();
        if (quality == null) {
            quality = 50;
        } else if (quality < 1 || quality > 100) {
            throw new RuntimeException("Quality must be between 1 and 100");
        }

        PdfJobDocument job = PdfJobDocument.builder()
                .userId(userId)
                .status(PdfJobStatus.PENDING)
                .jobType(PdfJobType.COMPRESS)
                .inputFileId(dto.getFileId())
                .quality(quality)
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        pdfProcessingService.processCompressJob(saved.getId(), dto.getFileId(), quality);
        return mapToDTO(saved);
    }

    public PdfJobDTO submitTranslateJob(TranslatePdfRequestDTO dto) {
        String userId = getCurrentUserId();
        FileMetaDataDocument file = getOwnedFile(dto.getFileId(), userId);
        if (!isDocx(file)) {
            throw new RuntimeException("Selected file must be a DOCX: " + file.getName());
        }

        String sourceLanguage = StringUtils.hasText(dto.getSourceLanguage()) ? dto.getSourceLanguage() : "auto";

        PdfJobDocument job = PdfJobDocument.builder()
                .userId(userId)
                .status(PdfJobStatus.PENDING)
                .jobType(PdfJobType.TRANSLATE)
                .inputFileId(dto.getFileId())
                .sourceLanguage(sourceLanguage)
                .targetLanguage(dto.getTargetLanguage())
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        docxTranslationService.processTranslateJob(saved.getId(), dto.getFileId(), sourceLanguage, dto.getTargetLanguage());
        return mapToDTO(saved);
    }

    public PdfJobDTO submitConvertFromPdfJob(ConvertFromPdfRequestDTO dto) {
        String userId = getCurrentUserId();
        FileMetaDataDocument file = getOwnedFile(dto.getFileId(), userId);
        if (!isPdf(file)) {
            throw new RuntimeException("Selected file is not a PDF: " + file.getName());
        }
        PdfJobType jobType = targetFormatToJobType(dto.getTargetFormat());

        PdfJobDocument job = PdfJobDocument.builder()
                .userId(userId)
                .status(PdfJobStatus.PENDING)
                .jobType(jobType)
                .inputFileId(dto.getFileId())
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        dispatchConversionJob(saved.getId(), dto.getFileId(), jobType);
        return mapToDTO(saved);
    }

    public PdfJobDTO submitConvertToPdfJob(ConvertToPdfRequestDTO dto) {
        String userId = getCurrentUserId();
        FileMetaDataDocument file = getOwnedFile(dto.getFileId(), userId);
        PdfJobType jobType = detectSourceJobType(file);

        PdfJobDocument job = PdfJobDocument.builder()
                .userId(userId)
                .status(PdfJobStatus.PENDING)
                .jobType(jobType)
                .inputFileId(dto.getFileId())
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        dispatchConversionJob(saved.getId(), dto.getFileId(), jobType);
        return mapToDTO(saved);
    }

    /**
     * Jobs submitted anonymously have a null {@code userId} and, like submitting/polling them,
     * are accessible to anyone who knows the job ID - login is only required to reach these
     * endpoints in the first place (see {@code SecurityConfig}), not to own the job. Jobs
     * submitted by a signed-in user are locked to that same user.
     */
    private PdfJobDocument getAccessibleJob(String jobId, String userId) {
        PdfJobDocument job = pdfJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        if (job.getUserId() != null && !job.getUserId().equals(userId)) {
            throw new RuntimeException("Job not found");
        }
        return job;
    }

    public PdfJobDTO getJob(String jobId) {
        String userId = getCurrentUserId();
        PdfJobDocument job = getAccessibleJob(jobId, userId);
        return mapToDTO(job);
    }

    /**
     * Streams a completed job's result and, once read, deletes the temp result file (and the
     * source file, if nothing else needs it) - the result can only be downloaded once. See
     * {@link PdfResultCleanupService#purgeJobFiles(PdfJobDocument)}.
     */
    public PdfJobResultFile downloadResult(String jobId) {
        String userId = getCurrentUserId();
        PdfJobDocument job = getAccessibleJob(jobId, userId);
        if (job.getStatus() != PdfJobStatus.COMPLETED || !StringUtils.hasText(job.getResultFilePath())) {
            throw new RuntimeException("Job result is not available for download");
        }

        byte[] content;
        try {
            content = Files.readAllBytes(Paths.get(job.getResultFilePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read job result file: " + e.getMessage(), e);
        }

        PdfJobResultFile result = new PdfJobResultFile(content, job.getResultFileName(), job.getResultContentType());
        pdfResultCleanupService.purgeJobFiles(job);
        return result;
    }

    public List<PdfJobDTO> listJobs() {
        String userId = getCurrentUserId();
        return pdfJobRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private PdfJobDTO mapToDTO(PdfJobDocument job) {
        return PdfJobDTO.builder()
                .id(job.getId())
                .userId(job.getUserId())
                .status(job.getStatus())
                .jobType(job.getJobType())
                .inputFileId(job.getInputFileId())
                .resultFileName(job.getResultFileName())
                .resultContentType(job.getResultContentType())
                .resultSize(job.getResultSize())
                .resultExpiresAt(job.getResultExpiresAt())
                .quality(job.getQuality())
                .sourceLanguage(job.getSourceLanguage())
                .targetLanguage(job.getTargetLanguage())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
