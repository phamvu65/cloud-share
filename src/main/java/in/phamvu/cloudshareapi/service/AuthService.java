package in.phamvu.cloudshareapi.service;

import com.stripe.model.Token;
import in.phamvu.cloudshareapi.document.UserCredits;
import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.request.LoginRequestDTO;
import in.phamvu.cloudshareapi.dto.request.RegisterRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TokenRefreshRequestDTO;
import in.phamvu.cloudshareapi.dto.response.JwtResponseDTO;
import in.phamvu.cloudshareapi.dto.response.TokenRefreshResponseDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.repository.UserCreditsRepository;
import in.phamvu.cloudshareapi.repository.UserRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import in.phamvu.cloudshareapi.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "AUTHENTICATION-SERVICE")
public class AuthService {
    private final UserRepository userRepository;
    private final UserCreditsRepository userCreditsRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public void resgisterUser(RegisterRequestDTO registerRequestDTO) {

        if (userRepository.existsByEmail(registerRequestDTO.getEmail()))
        {
            throw new InvalidDataException("Email already exists");
        }

        UserDocument userDocument = UserDocument.builder()
                .email(registerRequestDTO.getEmail())
                .firstName(registerRequestDTO.getFirstName())
                .lastName(registerRequestDTO.getLastName())
                .password(passwordEncoder.encode(registerRequestDTO.getPassword()))
                .roles(Set.of("ROLE_USER"))
                .build();
        userRepository.save(userDocument);

        UserCredits userCredits = UserCredits.builder()
                .userId(userDocument.getId())
                .credits(5)
                .plan("BASIC")
                .build();
        userCreditsRepository.save(userCredits);

        log.info("User registered successfully and 5 credits : {}", userDocument.getEmail());

    }

    public JwtResponseDTO login(LoginRequestDTO request) {
        log.info("Login request received for email: {}", request.getEmail());
        String email = request.getEmail();
        String password = request.getPassword();


        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Set<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toSet());

        String refreshToken = jwtUtils.generateRefreshToken(userDetails.getUsername(), userDetails.getId());
        String accessToken = jwtUtils.generateAccessToken(userDetails.getUsername(), userDetails.getId(), roles);

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
        if(!jwtUtils.validateJwtToken(requestRefreshToken)){
            throw new InvalidDataException("Invalid refresh token");
        }

        String email = jwtUtils.getUserNameFromJwtToken(requestRefreshToken);

        UserDocument user = userRepository.findByEmail(email).orElseThrow(()-> new UsernameNotFoundException("User not found"));

        String newAccessToken = jwtUtils.generateAccessToken(email, user.getId(), user.getRoles());
        String newRefreshToken = jwtUtils.generateRefreshToken(email, user.getId());

        log.info("Token refreshed for user: {}", email);

        return TokenRefreshResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();

    }
}
