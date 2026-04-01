package tech.vartaai.whatsappcrm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class VoiceTranscriptionService {

    private final ObjectMapper objectMapper;
    private final String openAiApiKey;

    public VoiceTranscriptionService(
            ObjectMapper objectMapper,
            @Value("${openai.api-key:#{null}}") String openAiApiKey) {
        this.objectMapper = objectMapper;
        this.openAiApiKey = openAiApiKey;
    }

    public boolean isAvailable() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /**
     * Transcribe audio bytes using OpenAI Whisper API.
     *
     * @param audioBytes raw audio file bytes
     * @param mimeType   e.g. "audio/ogg", "audio/mpeg"
     * @param filename   e.g. "voice.ogg"
     * @return transcribed text, or null if transcription fails
     */
    public String transcribe(byte[] audioBytes, String mimeType, String filename) {
        if (!isAvailable()) {
            log.warn("VOICE_TRANSCRIPTION_UNAVAILABLE — OPENAI_API_KEY not configured");
            return null;
        }
        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("VOICE_TRANSCRIPTION_EMPTY — no audio bytes provided");
            return null;
        }

        long start = System.currentTimeMillis();

        try {
            // Determine file extension from mime type
            String ext = switch (mimeType != null ? mimeType : "") {
                case "audio/ogg" -> "ogg";
                case "audio/mpeg", "audio/mp3" -> "mp3";
                case "audio/wav" -> "wav";
                case "audio/mp4", "audio/m4a" -> "m4a";
                case "audio/webm" -> "webm";
                default -> "ogg"; // WhatsApp default
            };
            String safeFilename = filename != null ? filename : "voice." + ext;

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return safeFilename;
                }
            }).contentType(MediaType.parseMediaType(mimeType != null ? mimeType : "audio/ogg"));
            builder.part("model", "whisper-1");

            WebClient webClient = WebClient.builder()
                    .baseUrl("https://api.openai.com")
                    .build();

            String responseBody = webClient.post()
                    .uri("/v1/audio/transcriptions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long latency = System.currentTimeMillis() - start;

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("text").asText("");

            log.info("VOICE_TRANSCRIPTION_SUCCESS length={} latencyMs={} textLength={}",
                    audioBytes.length, latency, text.length());
            return text;

        } catch (Exception e) {
            log.error("VOICE_TRANSCRIPTION_FAILED err={}", e.getMessage(), e);
            return null;
        }
    }
}
