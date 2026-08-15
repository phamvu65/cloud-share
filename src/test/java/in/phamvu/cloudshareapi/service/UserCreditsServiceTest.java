package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserCredits;
import in.phamvu.cloudshareapi.repository.UserCreditsRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCreditsServiceTest {

    @Mock
    private UserCreditsRepository userCreditsRepository;

    private UserCreditsService userCreditsService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        userCreditsService = new UserCreditsService(userCreditsRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, "user@example.com", "pw", List.of(), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
    }

    @Test
    void createInitialCredits_savesDefaultBasicPlanWithFiveCredits() {
        when(userCreditsRepository.save(any(UserCredits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCredits result = userCreditsService.createInitialCredits("user-1");

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getCredits()).isEqualTo(5);
        assertThat(result.getPlan()).isEqualTo("BASIC");
    }

    @Test
    void getUserCreditsById_existingUser_returnsStoredRecordWithoutCreatingNew() {
        UserCredits existing = UserCredits.builder().userId("user-1").credits(10).plan("PRO").build();
        when(userCreditsRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

        UserCredits result = userCreditsService.getUserCredits("user-1");

        assertThat(result).isSameAs(existing);
        verify(userCreditsRepository, never()).save(any());
    }

    @Test
    void getUserCreditsById_missingUser_createsInitialCredits() {
        when(userCreditsRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(userCreditsRepository.save(any(UserCredits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCredits result = userCreditsService.getUserCredits("user-1");

        assertThat(result.getCredits()).isEqualTo(5);
        assertThat(result.getPlan()).isEqualTo("BASIC");
    }

    @Test
    void hasEnoughCredits_balanceAboveThreshold_returnsTrue() {
        authenticateAs("user-1");
        when(userCreditsRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(UserCredits.builder().userId("user-1").credits(3).plan("BASIC").build()));

        assertThat(userCreditsService.hasEnoughCredits(2)).isTrue();
    }

    @Test
    void hasEnoughCredits_balanceBelowThreshold_returnsFalse() {
        authenticateAs("user-1");
        when(userCreditsRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(UserCredits.builder().userId("user-1").credits(1).plan("BASIC").build()));

        assertThat(userCreditsService.hasEnoughCredits(2)).isFalse();
    }

    @Test
    void consumeCredit_positiveBalance_decrementsAndSaves() {
        authenticateAs("user-1");
        when(userCreditsRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(UserCredits.builder().userId("user-1").credits(3).plan("BASIC").build()));
        when(userCreditsRepository.save(any(UserCredits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCredits result = userCreditsService.consumeCredit();

        assertThat(result.getCredits()).isEqualTo(2);
    }

    @Test
    void consumeCredit_zeroBalance_returnsNullWithoutSaving() {
        authenticateAs("user-1");
        when(userCreditsRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(UserCredits.builder().userId("user-1").credits(0).plan("BASIC").build()));

        UserCredits result = userCreditsService.consumeCredit();

        assertThat(result).isNull();
        verify(userCreditsRepository, never()).save(any());
    }

    @Test
    void addCredits_existingUser_addsOnTopOfBalanceAndUpdatesPlan() {
        UserCredits existing = UserCredits.builder().userId("user-1").credits(2).plan("BASIC").build();
        when(userCreditsRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(userCreditsRepository.save(any(UserCredits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCredits result = userCreditsService.addCredits("user-1", 10, "PRO");

        assertThat(result.getCredits()).isEqualTo(12);
        assertThat(result.getPlan()).isEqualTo("PRO");
    }

    @Test
    void addCredits_missingUser_createsInitialThenAddsOnTop() {
        when(userCreditsRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(userCreditsRepository.save(any(UserCredits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCredits result = userCreditsService.addCredits("user-1", 10, "PRO");

        assertThat(result.getCredits()).isEqualTo(15);
        assertThat(result.getPlan()).isEqualTo("PRO");
    }
}