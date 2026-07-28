package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.FileMetaDataDocument;
import in.phamvu.cloudshareapi.document.PdfJobDocument;
import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.dto.PdfJobDTO;
import in.phamvu.cloudshareapi.dto.request.CompressPdfRequestDTO;
import in.phamvu.cloudshareapi.repository.FileMetaDataRepository;
import in.phamvu.cloudshareapi.repository.PdfJobRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdfJobService {

    private static final int CREDIT_COST_PER_JOB = 1;

    private final PdfJobRepository pdfJobRepository;
    private final FileMetaDataRepository fileMetaDataRepository;
    private final UserCreditsService userCreditsService;
    private final PdfProcessingService pdfProcessingService;

    private String getCurrentUserId() {
        CustomUserDetails customUserDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return customUserDetails.getId();
    }

    private FileMetaDataDocument getOwnedPdfFile(String fileId, String userId) {
        FileMetaDataDocument file = fileMetaDataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("You don't have permission to access file: " + fileId);
        }
        boolean looksLikePdf = "application/pdf".equalsIgnoreCase(file.getType())
                || (file.getName() != null && file.getName().toLowerCase().endsWith(".pdf"));
        if (!looksLikePdf) {
            throw new RuntimeException("Selected file is not a PDF: " + file.getName());
        }
        return file;
    }

    private void consumeJobCredit() {
        if (!userCreditsService.hasEnoughCredits(CREDIT_COST_PER_JOB)) {
            throw new RuntimeException("Not enough credits to submit PDF job. Please purchase more credits");
        }
        userCreditsService.consumeCredit();
    }

    public PdfJobDTO submitCompressJob(CompressPdfRequestDTO dto) {
        String userId = getCurrentUserId();
        getOwnedPdfFile(dto.getFileId(), userId);

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
                .inputFileId(dto.getFileId())
                .quality(quality)
                .build();
        PdfJobDocument saved = pdfJobRepository.save(job);

        pdfProcessingService.processCompressJob(saved.getId(), userId, dto.getFileId(), quality);
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
                .inputFileId(job.getInputFileId())
                .resultFileId(job.getResultFileId())
                .quality(job.getQuality())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
