package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.request.SetPasswordRequestDTO;
import in.phamvu.cloudshareapi.dto.request.UpdateUserRequestDTO;
import in.phamvu.cloudshareapi.dto.response.UserResponseDTO;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import in.phamvu.cloudshareapi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {

        String userId = userDetails.getId();
        UserResponseDTO userResponseDTO = userService.getUserProfile(userId);

        return  ResponseEntity.ok(userResponseDTO);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserRequestDTO request) {

        String userId = userDetails.getId();
        UserResponseDTO userResponseDTO = userService.updateUser(userId, request);

        return ResponseEntity.ok(userResponseDTO);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> setPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SetPasswordRequestDTO request) {

        String userId = userDetails.getId();
        userService.setPassword(userId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {

        String userId = userDetails.getId();
        userService.deleteAccount(userId);

        return ResponseEntity.noContent().build();
    }
}
