package com.hackathon.resourceallocation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    public record AIAnalysisResult(
            String category,
            String urgency,
            int priorityScore,
            String reasoning,
            String suggestedSkills
    ) {}

    public AIAnalysisResult analyzeNeed(String title, String description) {
        String prompt = """
            You are an emergency response AI for an NGO disaster coordination system.
            Analyze the following community need and respond ONLY in valid JSON.
            Do not include markdown, backticks, or any extra text.

            Need Title: %s
            Description: %s

            Respond with EXACTLY this JSON structure:
            {
              "category": "FOOD|MEDICAL|SHELTER|WATER|OTHER",
              "urgency": "LOW|MEDIUM|HIGH|CRITICAL",
              "priority_score": <integer between 1 and 100>,
              "reasoning": "<one clear sentence explaining urgency assessment>",
              "suggested_skills": "<comma-separated skills like: medical,driving,cooking>"
            }
            """.formatted(title, description);

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{
                                    Map.of("text", prompt)
                            })
                    }
            );

            String response = webClient.post()
                    .uri(geminiApiUrl + "?key=" + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseGeminiResponse(response);

        } catch (Exception e) {
            log.error("AI analysis failed for need '{}': {}", title, e.getMessage());
            return fallbackAnalysis(title, description);
        }
    }

    private AIAnalysisResult parseGeminiResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        String text = root
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text").asText();

        // Strip markdown code fences if present
        text = text.replaceAll("```json", "").replaceAll("```", "").trim();

        JsonNode json = objectMapper.readTree(text);

        return new AIAnalysisResult(
                json.path("category").asText("OTHER"),
                json.path("urgency").asText("MEDIUM"),
                json.path("priority_score").asInt(50),
                json.path("reasoning").asText("AI analysis completed."),
                json.path("suggested_skills").asText("")
        );
    }

    // Keyword-based fallback if Gemini API fails or key not set
    private AIAnalysisResult fallbackAnalysis(String title, String description) {
        String combined = (title + " " + description).toLowerCase();

        String category = "OTHER";
        if (combined.matches(".*(food|meal|hungry|starv|eat|rice|water|drink).*")) category = "FOOD";
        else if (combined.matches(".*(medic|hospital|injur|sick|pain|insulin|blood|doctor|nurse|health).*")) category = "MEDICAL";
        else if (combined.matches(".*(shelter|home|house|roof|sleep|flood|fire|displacement).*")) category = "SHELTER";
        else if (combined.matches(".*(water|drink|contamin|borewell|tanker|pipeline).*")) category = "WATER";

        String urgency = "MEDIUM";
        int score = 50;
        if (combined.matches(".*(urgent|critical|emergency|immediate|dying|hour|life|death).*")) {
            urgency = "CRITICAL"; score = 90;
        } else if (combined.matches(".*(severe|serious|danger|quickly|now|days|child).*")) {
            urgency = "HIGH"; score = 72;
        } else if (combined.matches(".*(soon|needed|lacking|shortage|missing).*")) {
            urgency = "MEDIUM"; score = 55;
        } else {
            urgency = "LOW"; score = 30;
        }

        String skills = switch (category) {
            case "MEDICAL" -> "medical,first_aid,nursing";
            case "FOOD" -> "cooking,food_distribution";
            case "SHELTER" -> "construction,shelter_management";
            case "WATER" -> "water_supply,logistics";
            default -> "logistics,driving";
        };

        return new AIAnalysisResult(category, urgency, score,
                "Keyword-based fallback analysis (AI API unavailable).", skills);
    }
}