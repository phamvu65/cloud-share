package in.phamvu.cloudshareapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FileMetaDataDTO {

    private String id;
    private String userId;
    private String name;
    private String type;

    @JsonProperty("isPublic")
    private boolean isPublic;
    private Long size;
    private String fileLocation;
    private LocalDateTime uploadedAt;

}
