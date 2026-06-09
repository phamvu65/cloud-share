package in.phamvu.cloudshareapi.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenRefreshResponseDTO {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String type = "Bearer";
}
