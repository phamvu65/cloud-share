package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.SessionDocument;
import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.request.LoginRequestDTO;
import in.phamvu.cloudshareapi.dto.request.RegisterRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TokenRefreshRequestDTO;
import in.phamvu.cloudshareapi.dto.response.JwtResponseDTO;
import in.phamvu.cloudshareapi.dto.response.TokenRefreshResponseDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.model.AuthProvider;
import in.phamvu.cloudshareapi.repository.SessionRepository;
import in.phamvu.cloudshareapi.repository.UserCreditsRepository;
import in.phamvu.cloudshareapi.repository.UserRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import in.phamvu.cloudshareapi.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCreditsRepository userCreditsRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userCreditsRepository, sessionRepository,
                passwordEncoder, jwtUtils, authenticationManager);
    }

    @Test
    void registerUser_newEmail_savesUserAndGrantsFiveInitialCredits() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> {
            UserDocument doc = invocation.getArgument(0);
            doc.setId("user-1");
            return doc;
        });

        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFirstName("First");
        request.setLastName("Last");

        authService.registerUser(request);

        var userCaptor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getProvider()).isEqualTo(AuthProvider.LOCAL);

        var creditsCaptor = ArgumentCaptor.forClass(in.phamvu.cloudshareapi.document.UserCredits.class);
        verify(userCreditsRepository).save(creditsCaptor.capture());
        assertThat(creditsCaptor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(creditsCaptor.getValue().getCredits()).isEqualTo(5);
        assertThat(creditsCaptor.getValue().getPlan()).isEqualTo("BASIC");
    }

    @Test
    void registerUser_emailAlreadyExists_throwsAndSavesNothing() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("taken@example.com");
        request.setPassword("password123");
        request.setFirstName("First");
        request.setLastName("Last");

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(InvalidDataException.class);
        verify(userRepository, never()).save(any());
        verify(userCreditsRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_createsSessionAndReturnsTokens() {
        CustomUserDetails userDetails = new CustomUserDetails(
                "user-1", "user@example.com", "encoded-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
        Authentication authenticated = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(sessionRepository.save(any(SessionDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateRefreshToken(eq("user@example.com"), eq("user-1"), anyString())).thenReturn("refresh-token");
        when(jwtUtils.generateAccessToken(eq("user@example.com"), eq("user-1"), any(), anyString())).thenReturn("access-token");

        LoginRequestDTO request = new LoginRequestDTO();
        request.setIdentifier("user@example.com");
        request.setPassword("password123");

        String windowsChromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/115.0.0.0 Safari/537.36";
        JwtResponseDTO result = authService.login(request, windowsChromeUa, "1.2.3.4");

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getRole()).containsExactly("ROLE_USER");

        var sessionCaptor = ArgumentCaptor.forClass(SessionDocument.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(sessionCaptor.getValue().getOs()).isEqualTo("Windows");
        assertThat(sessionCaptor.getValue().getIpAddress()).isEqualTo("1.2.3.4");
    }

    @Test
    void refreshToken_invalidToken_throws() {
        when(jwtUtils.validateJwtToken("bad-token")).thenReturn(false);

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("bad-token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void refreshToken_wrongTokenType_throws() {
        when(jwtUtils.validateJwtToken("access-token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("access-token")).thenReturn("ACCESS");

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("access-token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void refreshToken_userNotFound_throws() {
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("token")).thenReturn("REFRESH");
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void refreshToken_deletedAccount_throws() {
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("token")).thenReturn("REFRESH");
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("user@example.com");
        UserDocument deletedUser = UserDocument.builder().id("user-1").email("user@example.com").deleted(true).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(deletedUser));

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void refreshToken_missingSid_throws() {
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("token")).thenReturn("REFRESH");
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("user@example.com");
        UserDocument user = UserDocument.builder().id("user-1").email("user@example.com").deleted(false).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.getSidFromJwtToken("token")).thenReturn(null);

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void refreshToken_sessionNotFound_throws() {
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("token")).thenReturn("REFRESH");
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("user@example.com");
        UserDocument user = UserDocument.builder().id("user-1").email("user@example.com").deleted(false).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.getSidFromJwtToken("token")).thenReturn("sid-1");
        when(sessionRepository.findById("sid-1")).thenReturn(Optional.empty());

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void refreshToken_revokedSession_throws() {
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("token")).thenReturn("REFRESH");
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("user@example.com");
        UserDocument user = UserDocument.builder().id("user-1").email("user@example.com").deleted(false).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.getSidFromJwtToken("token")).thenReturn("sid-1");
        SessionDocument session = SessionDocument.builder().id("sid-1").userId("user-1").revoked(true).build();
        when(sessionRepository.findById("sid-1")).thenReturn(Optional.of(session));

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void refreshToken_valid_returnsNewTokensAndTouchesSession() {
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getTokenTypeFromJwtToken("token")).thenReturn("REFRESH");
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("user@example.com");
        UserDocument user = UserDocument.builder().id("user-1").email("user@example.com")
                .deleted(false).roles(Set.of("ROLE_USER")).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.getSidFromJwtToken("token")).thenReturn("sid-1");
        SessionDocument session = SessionDocument.builder().id("sid-1").userId("user-1")
                .revoked(false).lastUsedAt(Instant.EPOCH).build();
        when(sessionRepository.findById("sid-1")).thenReturn(Optional.of(session));
        when(jwtUtils.generateAccessToken(eq("user@example.com"), eq("user-1"), any(), eq("sid-1"))).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken(eq("user@example.com"), eq("user-1"), eq("sid-1"))).thenReturn("new-refresh");

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("token");

        TokenRefreshResponseDTO result = authService.refreshToken(request);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(session.getLastUsedAt()).isAfter(Instant.EPOCH);
        verify(sessionRepository).save(session);
    }
}
