package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.ShortLinkDTO;
import in.phamvu.cloudshareapi.dto.request.CreateShortLinkRequestDTO;
import in.phamvu.cloudshareapi.service.ShortLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/short-links")
@RequiredArgsConstructor
@Slf4j(topic = "SHORT-LINK-CONTROLLER")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    /**
     * API to create a short link for any URL. Works without an account, same policy as
     * file upload - only listing/deleting a user's own links requires login.
     */
    @PostMapping
    public ResponseEntity<ShortLinkDTO> createShortLink(@Valid @RequestBody CreateShortLinkRequestDTO request) {
        log.info("Initiating create short link API");
        ShortLinkDTO shortLink = shortLinkService.createShortLink(request);
        log.info("Successfully created short link with code: {}", shortLink.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(shortLink);
    }

    /**
     * API to retrieve all short links belonging to the currently authenticated user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<ShortLinkDTO>> getMyShortLinks() {
        log.info("Initiating fetch short links API for the current user");
        List<ShortLinkDTO> list = shortLinkService.getMyLinks();
        log.info("Successfully retrieved {} short links for the current user", list.size());
        return ResponseEntity.ok(list);
    }

    /**
     * API to delete a short link (requires ownership).
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteShortLink(@PathVariable("code") String code) {
        log.info("Initiating delete short link API for code: {}", code);
        shortLinkService.deleteLink(code);
        log.info("Successfully deleted short link with code: {}", code);
        return ResponseEntity.noContent().build();
    }
}
