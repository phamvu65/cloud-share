package in.phamvu.cloudshareapi.dto.request;

import in.phamvu.cloudshareapi.document.ConvertFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ConvertFromPdfRequestDTO {

    @NotBlank
    private String fileId;

    /**
     * Format to convert the source PDF into.
     */
    @NotNull
    private ConvertFormat targetFormat;
}
