package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.response.SessionResponseDTO;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import in.phamvu.cloudshareapi.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/sessions")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<SessionResponseDTO>> listSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestAttribute(name = "sid", required = false) String currentSid) {

        List<SessionResponseDTO> sessions = sessionService.listSessions(userDetails.getId(), currentSid);

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/others")
    public ResponseEntity<Void> revokeOtherSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestAttribute(name = "sid", required = false) String currentSid) {

        sessionService.revokeOtherSessions(userDetails.getId(), currentSid);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        sessionService.revokeSession(userDetails.getId(), id);

        return ResponseEntity.noContent().build();
    }
}
