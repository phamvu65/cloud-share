package in.phamvu.cloudshareapi.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.util.Set;

@Builder
@Data
public class UserResponseDTO {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
