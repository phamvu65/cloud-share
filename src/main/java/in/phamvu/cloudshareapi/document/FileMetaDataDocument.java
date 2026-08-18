package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "files")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FileMetaDataDocument {

    @Id
    private String id;
    @Indexed
    private String userId;
    private String name;
    private String type;
    private Boolean isPublic;
    private Long size;
    private String fileLocation;

    @CreatedDate
    private LocalDateTime uploadedAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
