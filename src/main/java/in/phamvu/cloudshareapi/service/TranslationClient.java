package in.phamvu.cloudshareapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for a LibreTranslate-compatible REST API. Works against the public
 * instance or a self-hosted one via {@code translate.api.url}; {@code translate.api.key}
 * is optional and only sent when configured.
 */
@Service
@Slf4j(topic = "TRANSLATION-CLIENT")
public class TranslationClient {

    private final RestTemplate restTemplate;

    @Value("${translate.api.url}")
    private String apiUrl;

    @Value("${translate.api.key:}")
    private String apiKey;

    public TranslationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String translate(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.isBlank()) {
            return "";
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("q", text);
        body.put("source", StringUtils.hasText(sourceLanguage) ? sourceLanguage : "auto");
        body.put("target", targetLanguage);
        body.put("format", "text");
        if (StringUtils.hasText(apiKey)) {
            body.put("api_key", apiKey);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            Map<?, ?> response = restTemplate.postForObject(apiUrl, new HttpEntity<>(body, headers), Map.class);
            Object translated = response != null ? response.get("translatedText") : null;
            if (translated == null) {
                throw new RuntimeException("Translation API returned an empty response");
            }
            return translated.toString();
        } catch (Exception e) {
            log.error("Translation API call to {} failed", apiUrl, e);
            throw new RuntimeException("Failed to translate text: " + e.getMessage(), e);
        }
    }
}
