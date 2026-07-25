package in.phamvu.cloudshareapi.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import in.phamvu.cloudshareapi.document.SessionDocument;
import in.phamvu.cloudshareapi.document.UserCredits;
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
import in.phamvu.cloudshareapi.util.UserAgentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "AUTHENTICATION-SERVICE")
public class AuthService {
    private final UserRepository userRepository;
    private final UserCreditsRepository userCreditsRepository;
    private final SessionRepository sessionRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @Value("${google.client-id}")
    private String googleClientId;

    @Transactional
    public void registerUser(RegisterRequestDTO registerRequestDTO) {
        if (userRepository.existsByEmail(registerRequestDTO.getEmail())) {
            throw new InvalidDataException("Email already exists");
        }

        UserDocument userDocument = UserDocument.builder()
                .email(registerRequestDTO.getEmail())
                .firstName(registerRequestDTO.getFirstName())
                .lastName(registerRequestDTO.getLastName())
                .password(passwordEncoder.encode(registerRequestDTO.getPassword()))
                .roles(Set.of("ROLE_USER"))
                .provider(AuthProvider.LOCAL)
                .build();
        userRepository.save(userDocument);

        UserCredits userCredits = UserCredits.builder()
                .userId(userDocument.getId())
                .credits(5)
                .plan("BASIC")
                .build();
        userCreditsRepository.save(userCredits);

        log.info("User registered successfully with 5 credits: {}", userDocument.getEmail());
    }

    public JwtResponseDTO login(LoginRequestDTO request, String userAgent, String ipAddress) {
        log.info("Login request received for identifier: {}", request.getIdentifier());
        String identifier = request.getIdentifier();
        String password = request.getPassword();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, password)
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Set<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toSet());

        String sid = createSession(userDetails.getId(), userAgent, ipAddress);

        String refreshToken = jwtUtils.generateRefreshToken(userDetails.getUsername(), userDetails.getId(), sid);
        String accessToken = jwtUtils.generateAccessToken(userDetails.getUsername(), userDetails.getId(), roles, sid);

        log.info("User logged in successfully: {}", userDetails.getUsername());

        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(userDetails.getUsername())
                .role(roles)
                .build();
    }

    public TokenRefreshResponseDTO refreshToken(TokenRefreshRequestDTO tokenRefreshResponseDTO) {
        String requestRefreshToken = tokenRefreshResponseDTO.getRefreshToken();
        if (!jwtUtils.validateJwtToken(requestRefreshToken)) {
            throw new InvalidDataException("Invalid refresh token");
        }

        if (!"REFRESH".equals(jwtUtils.getTokenTypeFromJwtToken(requestRefreshToken))) {
            throw new InvalidDataException("Invalid token type. Refresh token required.");
        }

        String email = jwtUtils.getUserNameFromJwtToken(requestRefreshToken);

        UserDocument user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.isDeleted()) {
            throw new InvalidDataException("Account has been deleted");
        }

        String sid = jwtUtils.getSidFromJwtToken(requestRefreshToken);
        if (sid == null) {
            throw new InvalidDataException("Session expired, please login again");
        }

        SessionDocument session = sessionRepository.findById(sid)
                .orElseThrow(() -> new InvalidDataException("Session has been revoked"));

        if (session.isRevoked()) {
            throw new InvalidDataException("Session has been revoked");
        }

        session.setLastUsedAt(Instant.now());
        sessionRepository.save(session);

        String newAccessToken = jwtUtils.generateAccessToken(email, user.getId(), user.getRoles(), sid);
        String newRefreshToken = jwtUtils.generateRefreshToken(email, user.getId(), sid);

        log.info("Token refreshed for user: {}", email);

        return TokenRefreshResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private String createSession(String userId, String userAgent, String ipAddress) {
        UserAgentParser.ParsedUserAgent parsed = UserAgentParser.parse(userAgent);
        Instant now = Instant.now();

        SessionDocument session = SessionDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .userAgent(userAgent)
                .os(parsed.os())
                .browser(parsed.browser())
                .ipAddress(ipAddress)
                .createdAt(now)
                .lastUsedAt(now)
                .revoked(false)
                .build();
        sessionRepository.save(session);

        return session.getId();
    }

    @Transactional
    public JwtResponseDTO processGoogleLogin(String idTokenString, String userAgent, String ipAddress) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new InvalidDataException("Invalid Google Token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String firstName = (String) payload.get("given_name");
            String lastName = (String) payload.get("family_name");
            String pictureUrl = (String) payload.get("picture");

            UserDocument user = userRepository.findByEmail(email).orElseGet(() -> {
                UserDocument newUser = UserDocument.builder()
                        .email(email)
                        .firstName(firstName)
                        .lastName(lastName)
                        .photoUrl(pictureUrl)
                        .roles(Set.of("ROLE_USER"))
                        .provider(AuthProvider.GOOGLE)
                        .build();
                userRepository.save(newUser);

                UserCredits userCredits = UserCredits.builder()
                        .userId(newUser.getId())
                        .credits(5)
                        .plan("BASIC")
                        .build();
                userCreditsRepository.save(userCredits);

                log.info("New Google user registered with 5 credits: {}", email);
                return newUser;
            });

            if (user.isDeleted()) {
                throw new InvalidDataException("Account has been deleted");
            }

            String sid = createSession(user.getId(), userAgent, ipAddress);

            String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getId(), user.getRoles(), sid);
            String refreshToken = jwtUtils.generateRefreshToken(user.getEmail(), user.getId(), sid);

            log.info("Google login successful for user: {}", email);

            return JwtResponseDTO.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .email(user.getEmail())
                    .role(user.getRoles())
                    .build();

        } catch (InvalidDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google Authentication Failed: {}", e.getMessage(), e);
            throw new InvalidDataException("Google Authentication Failed: " + e.getMessage());
        }
    }
}
