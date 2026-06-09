package in.phamvu.cloudshareapi.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class JwtResponseDTO {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String type = "Bearer";
    private String email;
    private Set<String> role;
}
