package com.expensetracker.service;

import com.expensetracker.dto.response.AiExpenseResponse.AiClassifiedItem;
import com.expensetracker.dto.response.AiExpenseResponse.ExpenseType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI to split free-form expense text (e.g. "maggi and two chocolates")
 * into individual classified items. Falls back to null on any failure so the
 * caller (AiExpenseService) can drop back to the keyword classifier.
 */
@Slf4j
@Service
public class OpenAiExpenseClassifierService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.openai-api-key}")
    private String apiKey;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;


    @PostConstruct
    public void init() {
        log.info("Model: {}", model);
        log.info("API key configured: {}", apiKey != null && !apiKey.isBlank());

        if (apiKey != null && !apiKey.isBlank()) {
            log.info("Key prefix: {}...", apiKey.substring(0, Math.min(10, apiKey.length())));
        }
    }
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
        You are an expense-parsing assistant for a shared-expense app.
        The user will describe one or more purchases in casual natural language,
        e.g. "maggi and two chocolates" or "bought shampoo 200 and groceries 450".

        Split the text into individual expense items. Handle spelled-out
        quantities ("two chocolates" = one item, description "chocolates",
        note the quantity in the description, amount null if no price given).
        Do not guess a price if none is stated - leave amount null.

        For each item, classify it as one of:
        - PERSONAL: only benefits the individual (grooming, clothing, subscriptions, romantic)
        - GROUP_SHARED: benefits the household/group (groceries, rent, utilities, shared meals)
        - NEEDS_CONFIRMATION: genuinely ambiguous (alcohol, gifts, party items)

        Infer a category from exactly one of:
        FOOD, GROCERIES, RENT, UTILITIES, HEALTH, SHOPPING, ENTERTAINMENT, OTHER.

        Respond with ONLY valid JSON, no prose, no markdown fences, in this exact shape:
        {"items":[{"description":"string","amount":number|null,"category":"string",
        "type":"PERSONAL|GROUP_SHARED|NEEDS_CONFIRMATION","confidence":number,
        "reasoning":"string"}]}
        """;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Returns classified items, or null if the call failed / key isn't configured -
     * caller should fall back to the keyword classifier in that case.
     */
    public List<AiClassifiedItem> classify(String text) {
        if (!isConfigured()) {
            log.info("OpenAI API key not configured, skipping AI classification");
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "response_format", Map.of("type", "json_object"),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", text)
                )
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsed = objectMapper.readTree(content);

            List<AiClassifiedItem> items = new ArrayList<>();
            int i = 0;
            for (JsonNode node : parsed.path("items")) {
                items.add(AiClassifiedItem.builder()
                        .itemId("item-" + i++)
                        .description(node.path("description").asText())
                        .amount(node.hasNonNull("amount") ? new BigDecimal(node.path("amount").asText()) : null)
                        .category(node.path("category").asText("OTHER"))
                        .type(safeType(node.path("type").asText("NEEDS_CONFIRMATION")))
                        .confidence(node.path("confidence").asDouble(0.5))
                        .reasoning(node.path("reasoning").asText(""))
                        .build());
            }
            return items;

        } catch (Exception e) {
            log.warn("OpenAI classification failed, will fall back to keyword classifier: {}", e.getMessage());
            return null;
        }
    }

    private ExpenseType safeType(String raw) {
        try {
            return ExpenseType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ExpenseType.NEEDS_CONFIRMATION;
        }
    }
}
