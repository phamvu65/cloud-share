package in.phamvu.cloudshareapi.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Builder
@Data
public class UserResponseDTO {
    private String id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String photoUrl;
    private Set<String> roles;
}
