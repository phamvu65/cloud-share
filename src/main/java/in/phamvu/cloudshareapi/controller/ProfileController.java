package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.userDTO;
import in.phamvu.cloudshareapi.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @PostMapping("/register")
    public ResponseEntity<?> registerProfile(@RequestBody userDTO userDTO) {
//        HttpStatus status= profileService.exitsByClerkId(userDTO.getClerkId()) ? HttpStatus.OK : HttpStatus.CREATED;

        userDTO savedProfile = profileService.createProfile(userDTO);
        return ResponseEntity.status(HttpStatus.OK).body(savedProfile);
    }

}
