package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.document.PdfJobType;
import in.phamvu.cloudshareapi.dto.PdfJobDTO;
import in.phamvu.cloudshareapi.dto.request.CompressPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TranslatePdfRequestDTO;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdfJobService {

    private static final int CREDIT_COST_PER_JOB = 1;
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final UserCreditsService userCreditsService;
    private final PdfProcessingService pdfProcessingService;
    private final DocxTranslationService docxTranslationService;

    private String getCurrentUserId() {
        CustomUserDetails customUserDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return customUserDetails.getId();
    }

    private FileMetaDataDocument getOwnedFile(String fileId, String userId) {
        FileMetaDataDocument file = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
        if (!file.getUserId().equals(userId)) {
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

    private void consumeJobCredit() {
        if (!userCreditsService.hasEnoughCredits(CREDIT_COST_PER_JOB)) {
            throw new RuntimeException("Not enough credits to submit PDF job. Please purchase more credits");
        }
        userCreditsService.consumeCredit();
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
        consumeJobCredit();

        PdfJobDocument job = PdfJobDocument.builder()
                .userId(userId)
                .status(PdfJobStatus.PENDING)
                .jobType(PdfJobType.COMPRESS)
                .inputFileId(dto.getFileId())
                .quality(quality)
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        pdfProcessingService.processCompressJob(saved.getId(), userId, dto.getFileId(), quality);
        return mapToDTO(saved);
    }

    public PdfJobDTO submitTranslateJob(TranslatePdfRequestDTO dto) {
        String userId = getCurrentUserId();
        FileMetaDataDocument file = getOwnedFile(dto.getFileId(), userId);
        if (!isDocx(file)) {
            throw new RuntimeException("Selected file must be a DOCX: " + file.getName());
        }

        String sourceLanguage = StringUtils.hasText(dto.getSourceLanguage()) ? dto.getSourceLanguage() : "auto";
        consumeJobCredit();

        PdfJobDocument job = PdfJobDocument.builder()
                .userId(userId)
                .status(PdfJobStatus.PENDING)
                .jobType(PdfJobType.TRANSLATE)
                .inputFileId(dto.getFileId())
                .sourceLanguage(sourceLanguage)
                .targetLanguage(dto.getTargetLanguage())
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        docxTranslationService.processTranslateJob(saved.getId(), userId, dto.getFileId(), sourceLanguage, dto.getTargetLanguage());
        return mapToDTO(saved);
    }

    public PdfJobDTO getJob(String jobId) {
        String userId = getCurrentUserId();
        PdfJobDocument job = pdfJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        return mapToDTO(job);
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
                .resultFileId(job.getResultFileId())
                .quality(job.getQuality())
                .sourceLanguage(job.getSourceLanguage())
                .targetLanguage(job.getTargetLanguage())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
