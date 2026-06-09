package in.phamvu.cloudshareapi.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Date;

@Data
@Builder
public class ErrorResponse {
    private Date timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}