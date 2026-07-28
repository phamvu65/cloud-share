package in.phamvu.cloudshareapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CompressPdfRequestDTO {

    @NotBlank
    private String fileId;

    /**
     * JPEG re-encode quality 1-100. Null defaults to 50.
     */
    private Integer quality;
}
