package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "short_links")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ShortLinkDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String originalUrl;

    @Indexed
    private String userId;

    private Long clickCount;
    private Boolean active;

    @CreatedDate
    private LocalDateTime createdAt;
}
