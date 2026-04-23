package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.ProfileDTO;
import in.phamvu.cloudshareapi.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/register")
    public ResponseEntity<?> registerProfile(@RequestBody ProfileDTO profileDTO) {
        ProfileDTO savedProfile = profileService.createProfile(profileDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedProfile);
    }
}
