package com.hackathon.resourceallocation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiVisionService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    // ── Result record returned to callers ────────────────────────

    public record ImageAnalysisResult(
            String title,
            String description,
            String category,       // FOOD | MEDICAL | SHELTER | WATER | OTHER
            String urgency,        // LOW | MEDIUM | HIGH | CRITICAL
            int priorityScore,     // 1–100
            String reasoning,
            String suggestedSkills,
            String locationHint,   // any location text visible in image
            boolean analysisSuccessful
    ) {}

    // ── Main method: analyze an image given its raw bytes ─────────

    public ImageAnalysisResult analyzeImage(byte[] imageBytes, String mimeType) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = """
            You are an emergency response AI for an NGO disaster coordination system.
            
            Carefully analyze this image and identify any community needs, distress signals,
            or emergency situations visible. This could be:
            - People in distress or needing help
            - Damaged infrastructure (flooded roads, collapsed buildings)
            - Shortage signs (empty food stalls, dry taps, burned homes)
            - Handwritten or printed distress messages or banners
            - Overcrowded relief camps or queues
            - Sick or injured individuals
            - Environmental hazards (fire, flood, drought, contamination)
            
            Based on what you see, respond ONLY in valid JSON with no markdown or backticks:
            {
              "title": "<short 5-10 word title describing the visible need>",
              "description": "<2-3 sentence description of what is visible and why help is needed>",
              "category": "FOOD|MEDICAL|SHELTER|WATER|OTHER",
              "urgency": "LOW|MEDIUM|HIGH|CRITICAL",
              "priority_score": <integer 1-100>,
              "reasoning": "<one sentence explaining your urgency assessment>",
              "suggested_skills": "<comma-separated: e.g. medical,driving,food_distribution>",
              "location_hint": "<any visible text, signs, or landmarks indicating location, or empty string>",
              "confidence": "HIGH|MEDIUM|LOW"
            }
            
            If the image does NOT show any emergency or community need, respond with:
            {
              "title": "No emergency detected",
              "description": "The image does not appear to show an emergency situation.",
              "category": "OTHER",
              "urgency": "LOW",
              "priority_score": 5,
              "reasoning": "No visible signs of emergency or distress in the image.",
              "suggested_skills": "",
              "location_hint": "",
              "confidence": "HIGH"
            }
            """;

        try {
            // Build Gemini request with inline image data
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of(
                                            "inline_data", Map.of(
                                                    "mime_type", mimeType,
                                                    "data", base64Image
                                            )
                                    ),
                                    Map.of("text", prompt)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,    // low temp = consistent structured output
                            "maxOutputTokens", 600
                    )
            );

            String response = webClient.post()
                    .uri(geminiApiUrl + "?key=" + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);

        } catch (Exception e) {
            log.error("Gemini Vision API call failed: {}", e.getMessage());
            return fallbackResult();
        }
    }

    // ── Parse Gemini's JSON response ──────────────────────────────

    private ImageAnalysisResult parseResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            // Strip any accidental markdown fences
            text = text.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();

            JsonNode json = objectMapper.readTree(text);

            return new ImageAnalysisResult(
                    json.path("title").asText("Unspecified community need"),
                    json.path("description").asText("Details extracted from image."),
                    sanitizeEnum(json.path("category").asText("OTHER"),
                            new String[]{"FOOD","MEDICAL","SHELTER","WATER","OTHER"}, "OTHER"),
                    sanitizeEnum(json.path("urgency").asText("MEDIUM"),
                            new String[]{"LOW","MEDIUM","HIGH","CRITICAL"}, "MEDIUM"),
                    clamp(json.path("priority_score").asInt(50), 1, 100),
                    json.path("reasoning").asText("Image-based analysis."),
                    json.path("suggested_skills").asText(""),
                    json.path("location_hint").asText(""),
                    true
            );
        } catch (Exception e) {
            log.error("Failed to parse Gemini Vision response: {}", e.getMessage());
            return fallbackResult();
        }
    }

    // ── Fallback when API is unavailable ─────────────────────────

    private ImageAnalysisResult fallbackResult() {
        return new ImageAnalysisResult(
                "Image report submitted",
                "An image was uploaded by a reporter. Manual review required as AI analysis is currently unavailable.",
                "OTHER", "MEDIUM", 50,
                "AI vision analysis unavailable — manual review required.",
                "logistics",
                "",
                false
        );
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String sanitizeEnum(String value, String[] allowed, String defaultValue) {
        for (String a : allowed) {
            if (a.equalsIgnoreCase(value)) return a;
        }
        return defaultValue;
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}