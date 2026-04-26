
package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserCredits;
import in.phamvu.cloudshareapi.repository.UserCreditsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCreditsService {

    private final UserCreditsRepository userCreditsRepository;
    public UserCredits createInitialCredits(String clerkId) {
        UserCredits credits = UserCredits.builder()
                .clerkId(clerkId)
                .credits(5)
                .plan("BASIC")
                .build();

        return userCreditsRepository.save(credits);
    }
}
