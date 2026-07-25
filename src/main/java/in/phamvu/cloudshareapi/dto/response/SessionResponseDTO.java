package in.phamvu.cloudshareapi.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
public class SessionResponseDTO {
    private String id;
    private String os;
    private String browser;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastUsedAt;
    private boolean current;
}
