package in.phamvu.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_credits")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCredits {
    @Id
    private String id;
    @Indexed
    private String userId;
    private Integer credits;
    private String plan;
}
