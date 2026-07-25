package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.request.GoogleAuthRequest;
import in.phamvu.cloudshareapi.dto.request.LoginRequestDTO;
import in.phamvu.cloudshareapi.dto.request.RegisterRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TokenRefreshRequestDTO;
import in.phamvu.cloudshareapi.dto.response.JwtResponseDTO;
import in.phamvu.cloudshareapi.dto.response.TokenRefreshResponseDTO;
import in.phamvu.cloudshareapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> authenticateUser(@Valid @RequestBody LoginRequestDTO loginRequestDTO,
                                                            HttpServletRequest httpRequest) {
        JwtResponseDTO response = authService.login(loginRequestDTO, httpRequest.getHeader("User-Agent"), resolveClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO) {
        authService.registerUser(registerRequestDTO);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/google")
    public ResponseEntity<JwtResponseDTO> googleAuth(@RequestBody GoogleAuthRequest request,
                                                      HttpServletRequest httpRequest) {
        JwtResponseDTO response = authService.processGoogleLogin(request.idToken(), httpRequest.getHeader("User-Agent"), resolveClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refresh")
    public ResponseEntity<TokenRefreshResponseDTO> getRefreshToken(@Valid @RequestBody TokenRefreshRequestDTO tokenRefreshRequestDTO) {
        TokenRefreshResponseDTO token = authService.refreshToken(tokenRefreshRequestDTO);

        return ResponseEntity.ok(token);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
