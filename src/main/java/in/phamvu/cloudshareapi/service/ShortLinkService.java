package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.ShortLinkDocument;
import in.phamvu.cloudshareapi.dto.ShortLinkDTO;
import in.phamvu.cloudshareapi.dto.request.CreateShortLinkRequestDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.repository.ShortLinkRepository;
import in.phamvu.cloudshareapi.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class ShortLinkService {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShortLinkRepository shortLinkRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${app.short-link.base-url}")
    private String baseUrl;

    @Value("${app.short-link.code-length}")
    private int codeLength;

    /**
     * Null for anonymous callers - creating a short link doesn't require an account,
     * same policy as file upload.
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails customUserDetails)) {
            return null;
        }
        return customUserDetails.getId();
    }

    public ShortLinkDTO createShortLink(CreateShortLinkRequestDTO request) {
        String originalUrl = request.getOriginalUrl().trim();
        validateOriginalUrl(originalUrl);

        String code;
        if (StringUtils.hasText(request.getCustomCode())) {
            code = request.getCustomCode();
            if (shortLinkRepository.existsByCode(code)) {
                throw new InvalidDataException("Custom code '" + code + "' is already taken");
            }
        } else {
            code = generateUniqueCode();
        }

        ShortLinkDocument document = ShortLinkDocument.builder()
                .code(code)
                .originalUrl(originalUrl)
                .userId(getCurrentUserId())
                .clickCount(0L)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return mapToDTO(shortLinkRepository.save(document));
    }

    public String resolve(String code) {
        ShortLinkDocument link = shortLinkRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Short link not found"));

        mongoTemplate.updateFirst(
                Query.query(where("id").is(link.getId())),
                new Update().inc("clickCount", 1),
                ShortLinkDocument.class);

        return link.getOriginalUrl();
    }

    public List<ShortLinkDTO> getMyLinks() {
        String currentUserId = getCurrentUserId();
        return shortLinkRepository.findByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void deleteLink(String code) {
        String currentUserId = getCurrentUserId();
        ShortLinkDocument link = shortLinkRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Short link not found"));

        if (!Objects.equals(link.getUserId(), currentUserId)) {
            throw new RuntimeException("You don't have permission to delete this short link");
        }

        shortLinkRepository.deleteById(link.getId());
    }

    private void validateOriginalUrl(String originalUrl) {
        URI uri;
        try {
            uri = new URI(originalUrl);
        } catch (URISyntaxException e) {
            throw new InvalidDataException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidDataException("Only http and https URLs are allowed");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new InvalidDataException("URL must include a host");
        }
        if (StringUtils.hasText(baseUrl) && originalUrl.startsWith(baseUrl)) {
            throw new InvalidDataException("Cannot shorten a URL that points back to this service");
        }
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (shortLinkRepository.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private ShortLinkDTO mapToDTO(ShortLinkDocument document) {
        return ShortLinkDTO.builder()
                .code(document.getCode())
                .shortUrl(baseUrl + "/s/" + document.getCode())
                .originalUrl(document.getOriginalUrl())
                .clickCount(document.getClickCount())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
