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
public class TranslatePdfRequestDTO {

    @NotBlank
    private String fileId;

    /**
     * ISO 639-1 target language code, e.g. "vi", "en".
     */
    @NotBlank
    private String targetLanguage;

    /**
     * ISO 639-1 source language code. Blank/null defaults to "auto" (auto-detect).
     */
    private String sourceLanguage;
}
