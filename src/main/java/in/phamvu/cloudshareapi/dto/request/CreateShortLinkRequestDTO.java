package in.phamvu.cloudshareapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateShortLinkRequestDTO {

    @NotBlank(message = "Original URL is required")
    private String originalUrl;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{4,20}$", message = "Custom code must be 4-20 characters long and contain only letters, numbers, hyphens or underscores")
    private String customCode;
}
