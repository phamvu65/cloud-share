package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.document.UserCredits;
import in.phamvu.cloudshareapi.dto.UserCreditsDTO;
import in.phamvu.cloudshareapi.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserCreditsController {

    private final UserCreditsService userCreditsService;

    @GetMapping("/credits")
    public ResponseEntity<?> getUserCredits() {
        UserCredits userCredits = userCreditsService.getUserCredits();
        UserCreditsDTO userCreditsDTO =UserCreditsDTO.builder()
                .credits(userCredits.getCredits())
                .plan(userCredits.getPlan())
                .build();
        return ResponseEntity.ok(userCreditsDTO);
    }
}
