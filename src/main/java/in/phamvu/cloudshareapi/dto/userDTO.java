package in.phamvu.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class userDTO {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private Integer credits;
    private String photoUrl;
    private Instant createdAt;
}