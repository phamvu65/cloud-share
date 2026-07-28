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
    private String inputFileId;
    private String resultFileId;
    private Integer quality;
    private String errorMessage;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
