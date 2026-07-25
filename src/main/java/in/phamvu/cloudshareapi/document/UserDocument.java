package in.phamvu.cloudshareapi.document;

import in.phamvu.cloudshareapi.model.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Document(collection = "users")
public class UserDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true, sparse = true)
    private String username;

    private String password;

    private String firstName;

    private String lastName;

    private String photoUrl;

    private AuthProvider provider = AuthProvider.LOCAL;

    private Set<String> roles;

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;

    @CreatedDate
    private Instant createdAt;
}
