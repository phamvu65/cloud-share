package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@Slf4j(topic = "SHORT-LINK-REDIRECT-CONTROLLER")
public class ShortLinkRedirectController {

    private final ShortLinkService shortLinkService;

    /**
     * Public redirect endpoint. Resolves a short code to its original URL and issues a
     * 302 so the click isn't cached by browsers/proxies (the target can change if the
     * link is recreated).
     */
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code) {
        log.info("Resolving short link code: {}", code);
        String originalUrl = shortLinkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
