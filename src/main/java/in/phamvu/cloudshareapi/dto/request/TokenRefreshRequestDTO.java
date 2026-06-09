package in.phamvu.cloudshareapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequestDTO {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
