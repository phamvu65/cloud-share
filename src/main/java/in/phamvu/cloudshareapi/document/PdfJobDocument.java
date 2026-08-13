package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "pdf_jobs")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PdfJobDocument {

    @Id
    private String id;
    private String userId;
    private PdfJobStatus status;
    private PdfJobType jobType;
    private String inputFileId;

    /**
     * Result is never persisted as a permanent {@code FileMetaDataDocument} - it lives only as
     * a temp file on disk, referenced here, until it is downloaded once or {@link #resultExpiresAt}
     * passes (whichever comes first). {@code resultFilePath} is an on-disk path and is intentionally
     * never exposed to clients (see {@link in.phamvu.cloudshareapi.dto.PdfJobDTO}).
     */
    private String resultFilePath;
    private String resultFileName;
    private String resultContentType;
    private Long resultSize;
    private LocalDateTime resultExpiresAt;

    private Integer quality;
    private String sourceLanguage;
    private String targetLanguage;
    private String errorMessage;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
