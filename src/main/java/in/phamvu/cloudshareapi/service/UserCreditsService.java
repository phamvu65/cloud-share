
package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserCredits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCreditsService {

    public UserCredits createInitialCredits(String clerkId) {
        UserCredits credits = UserCredits.builder()
                .clerkId(clerkId)
                .credits(5)
                .plan("BASIC")
                .build();
        return credits;
    }
}
