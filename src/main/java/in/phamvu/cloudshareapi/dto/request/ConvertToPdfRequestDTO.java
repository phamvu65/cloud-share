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
public class ConvertToPdfRequestDTO {

    /**
     * Source format is auto-detected from the file's stored MIME type/extension.
     */
    @NotBlank
    private String fileId;

}
