package in.phamvu.cloudshareapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for a self-hosted Ollama instance running a small instruction-tuned model
 * (e.g. {@code qwen2.5:3b}). Unlike a dedicated NMT model, this understands paragraph-level
 * context. Called once per batch of paragraphs rather than once per paragraph - the
 * self-hosted model runs on CPU, so per-call overhead needs to be amortized across many
 * paragraphs rather than paid per paragraph.
 */
@Service
@Slf4j(topic = "TRANSLATION-CLIENT")
public class TranslationClient {

    /**
     * Kept small on purpose: total generation time is driven by total output tokens, not by
     * how they're split into requests, so a smaller batch doesn't cost meaningful extra time -
     * but it does mean a crashed/misaligned chunk only loses a handful of paragraphs, and
     * progress is visible in the logs every {@link #BATCH_SIZE} paragraphs instead of once
     * per (potentially very long) run.
     */
    private static final int BATCH_SIZE = 8;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ollama.api.url}")
    private String apiUrl;

    @Value("${ollama.model}")
    private String model;

    @Value("${ollama.num-thread}")
    private int numThread;

    @Value("${ollama.num-ctx}")
    private int numCtx;

    /**
     * A small CPU-hosted model occasionally emits malformed/truncated JSON (e.g. the context
     * window fills up before the array closes). Retry the same chunk before giving up, and if
     * it still fails, halve it - a smaller chunk needs less generated output to complete, which
     * both dodges context exhaustion and narrows the blast radius of a bad response.
     */
    private static final int MAX_ATTEMPTS_PER_CHUNK = 2;

    public TranslationClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Translates {@code texts} in order, chunked into batches of {@link #BATCH_SIZE} so each
     * Ollama call stays within a reasonable prompt size for a small CPU-hosted model.
     */
    public List<String> translateBatch(List<String> texts, String sourceLanguage, String targetLanguage) {
        List<String> results = new ArrayList<>(texts.size());
        int totalChunks = (texts.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        int chunkNumber = 0;
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            chunkNumber++;
            List<String> chunk = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            long startedAt = System.currentTimeMillis();
            results.addAll(translateChunk(chunk, sourceLanguage, targetLanguage));
            log.info("Translated chunk {}/{} ({} paragraphs) in {}s", chunkNumber, totalChunks, chunk.size(),
                    (System.currentTimeMillis() - startedAt) / 1000);
        }
        return results;
    }

    private List<String> translateChunk(List<String> chunk, String sourceLanguage, String targetLanguage) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_CHUNK; attempt++) {
            try {
                return callModel(chunk, sourceLanguage, targetLanguage);
            } catch (RuntimeException e) {
                if (attempt < MAX_ATTEMPTS_PER_CHUNK) {
                    log.warn("Chunk translation attempt {}/{} failed ({}), retrying", attempt,
                            MAX_ATTEMPTS_PER_CHUNK, e.getMessage());
                    continue;
                }
                if (chunk.size() == 1) {
                    throw e;
                }
                log.warn("Chunk of {} paragraphs still failing after {} attempts ({}), splitting in half",
                        chunk.size(), MAX_ATTEMPTS_PER_CHUNK, e.getMessage());
                int mid = chunk.size() / 2;
                List<String> result = new ArrayList<>(
                        translateChunk(chunk.subList(0, mid), sourceLanguage, targetLanguage));
                result.addAll(translateChunk(chunk.subList(mid, chunk.size()), sourceLanguage, targetLanguage));
                return result;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private List<String> callModel(List<String> chunk, String sourceLanguage, String targetLanguage) {
        String systemPrompt = "You are a professional document translator. Translate each string in the "
                + "\"texts\" array below from " + sourceLanguage + " to " + targetLanguage + ", preserving "
                + "meaning and tone. Respond with ONLY a JSON object of the form {\"translations\": [\"...\"]} "
                + "containing exactly " + chunk.size() + " strings, in the same order as the input. Do not "
                + "add explanations or extra text.";

        String userContent;
        try {
            userContent = objectMapper.writeValueAsString(Map.of("texts", chunk));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize texts for translation", e);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.put("format", "json");
        requestBody.put("options", Map.of("num_thread", numThread, "num_ctx", numCtx));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JsonNode response;
        try {
            response = restTemplate.postForObject(apiUrl, new HttpEntity<>(requestBody, headers), JsonNode.class);
        } catch (Exception e) {
            log.error("Ollama API call to {} failed", apiUrl, e);
            throw new RuntimeException("Failed to translate text: " + e.getMessage(), e);
        }

        String content = response != null ? response.path("message").path("content").asText(null) : null;
        if (content == null) {
            throw new RuntimeException("Translation API returned an empty response");
        }

        List<String> translations = parseTranslations(content);
        if (translations.size() != chunk.size()) {
            throw new RuntimeException("Translation model returned " + translations.size()
                    + " results for a batch of " + chunk.size() + " - refusing to apply a misaligned translation");
        }
        return translations;
    }

    private List<String> parseTranslations(String content) {
        try {
            JsonNode translationsNode = objectMapper.readTree(content).path("translations");
            List<String> result = new ArrayList<>();
            translationsNode.forEach(node -> result.add(node.asText()));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Translation model did not return valid JSON: " + e.getMessage(), e);
        }
    }
}
