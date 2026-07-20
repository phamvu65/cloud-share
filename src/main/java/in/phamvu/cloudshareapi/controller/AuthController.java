package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.request.GoogleAuthRequest;
import in.phamvu.cloudshareapi.dto.request.LoginRequestDTO;
import in.phamvu.cloudshareapi.dto.request.RegisterRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TokenRefreshRequestDTO;
import in.phamvu.cloudshareapi.dto.response.JwtResponseDTO;
import in.phamvu.cloudshareapi.dto.response.TokenRefreshResponseDTO;
import in.phamvu.cloudshareapi.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> authenticateUser(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        JwtResponseDTO response = authService.login(loginRequestDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO) {
        authService.registerUser(registerRequestDTO);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/google")
    public ResponseEntity<JwtResponseDTO> googleAuth(@RequestBody GoogleAuthRequest request) {
        JwtResponseDTO response = authService.processGoogleLogin(request.idToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refresh")
    public ResponseEntity<TokenRefreshResponseDTO> getRefreshToken(@Valid @RequestBody TokenRefreshRequestDTO tokenRefreshRequestDTO) {
        TokenRefreshResponseDTO token = authService.refreshToken(tokenRefreshRequestDTO);

        return ResponseEntity.ok(token);
    }
}
