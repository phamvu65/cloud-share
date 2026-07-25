package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Document(collection = "sessions")
public class SessionDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userAgent;

    private String os;

    private String browser;

    private String ipAddress;

    private Instant createdAt;

    private Instant lastUsedAt;

    @Builder.Default
    private boolean revoked = false;

    private Instant revokedAt;
}
