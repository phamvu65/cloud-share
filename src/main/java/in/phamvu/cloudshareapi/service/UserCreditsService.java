
package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserCredits;
import in.phamvu.cloudshareapi.repository.UserCreditsRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCreditsService {

    private final UserCreditsRepository userCreditsRepository;

    public UserCredits createInitialCredits(String userId) {
        UserCredits userCredits = UserCredits.builder()
                .userId(userId)
                .credits(5)
                .plan("BASIC")
                .build();
        return userCreditsRepository.save(userCredits);
    }

    public UserCredits getUserCredits(String userId) {
        return userCreditsRepository.findByUserId(userId)
                .orElseGet(() -> createInitialCredits(userId));
    }

    public UserCredits getUserCredits() {
        CustomUserDetails userDetails = (CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return getUserCredits(userDetails.getId());
    }

    public Boolean hasEnoughCredits(int requiredCredits) {
        UserCredits userCredits = getUserCredits();
        return userCredits.getCredits() >= requiredCredits;
    }

    public UserCredits consumeCredit() {
        UserCredits userCredits = getUserCredits();

        if (userCredits.getCredits() <= 0) {
            return null;
        }

        userCredits.setCredits(userCredits.getCredits() - 1);
        return userCreditsRepository.save(userCredits);
    }

    public UserCredits addCredits(String userId, Integer creditsToadd, String plan) {
        UserCredits userCredits = userCreditsRepository.findByUserId(userId)
                .orElseGet(() -> createInitialCredits(userId));
        userCredits.setCredits(userCredits.getCredits() + creditsToadd);
        userCredits.setPlan(plan);
        return userCreditsRepository.save(userCredits);
    }
}