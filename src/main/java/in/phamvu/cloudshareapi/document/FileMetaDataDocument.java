package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
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
    private String clerkId;
    private String name;
    private String type;
    private Boolean isPublic;
    private Long size;
    private String fileLocation;
    private LocalDateTime uploadedAt;
}
