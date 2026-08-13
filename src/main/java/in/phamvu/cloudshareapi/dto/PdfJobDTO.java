package in.phamvu.cloudshareapi.dto;

import in.phamvu.cloudshareapi.document.PdfJobStatus;
import in.phamvu.cloudshareapi.document.PdfJobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PdfJobDTO {

    private String id;
    private String userId;
    private PdfJobStatus status;
    private PdfJobType jobType;
    private String inputFileId;
    private String resultFileName;
    private String resultContentType;
    private Long resultSize;
    private LocalDateTime resultExpiresAt;
    private Integer quality;
    private String sourceLanguage;
    private String targetLanguage;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
