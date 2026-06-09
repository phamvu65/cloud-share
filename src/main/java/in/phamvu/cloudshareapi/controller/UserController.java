package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.response.UserResponseDTO;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import in.phamvu.cloudshareapi.service.UserService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

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
}
