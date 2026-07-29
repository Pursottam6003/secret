package com.expensetracker.service;

import com.expensetracker.dto.request.ConfirmExpensesRequest;
import com.expensetracker.dto.request.ConfirmExpensesRequest.ConfirmedItem;
import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.dto.request.PersonalExpenseRequest;
import com.expensetracker.dto.response.AiExpenseResponse;
import com.expensetracker.dto.response.AiExpenseResponse.AiClassifiedItem;
import com.expensetracker.dto.response.AiExpenseResponse.ExpenseType;
import com.expensetracker.model.Group;
import com.expensetracker.model.enums.SplitType;
import com.expensetracker.repository.GroupRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword-based AI classifier stub.
 * Replace classifyWithAi() body with a real Gemini/OpenAI call when an API key is available.
 */
@Service
@RequiredArgsConstructor
public class AiExpenseService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final PersonalExpenseService personalExpenseService;
    private final OpenAiExpenseClassifierService openAiClassifier;


    // ─── Keyword dictionaries ─────────────────────────────────────────────────

    private static final Set<String> PERSONAL_KEYWORDS = Set.of(
            "shampoo", "conditioner", "perfume", "deodorant", "cologne",
            "moisturizer", "makeup", "lipstick", "foundation", "serum",
            "toothpaste", "razor", "blade", "haircut", "haircolor",
            "gf", "girlfriend", "bf", "boyfriend", "date", "romantic",
            "clothes", "shirt", "jeans", "dress", "shoes", "sneakers",
            "medicine", "tablet", "doctor", "hospital", "pharmacy",
            "gym", "subscription", "netflix", "spotify", "amazon prime"
    );

    private static final Set<String> SHARED_KEYWORDS = Set.of(
            "vegetables", "veggies", "potato", "potatoes", "onion", "tomato",
            "chilli", "chili", "pepper", "garlic", "ginger", "spinach",
            "grocery", "groceries", "rice", "dal", "lentils", "milk",
            "bread", "eggs", "butter", "oil", "flour", "sugar", "salt",
            "rent", "electricity", "internet", "wifi", "gas", "water bill",
            "maintenance", "repair", "cleaning", "detergent", "dishwash",
            "toilet paper", "tissue", "kitchen", "household",
            "dinner", "lunch", "breakfast", "restaurant", "food delivery",
            "pizza", "burger", "snacks", "chai", "coffee"
    );

    private static final Set<String> NEEDS_CONFIRMATION_KEYWORDS = Set.of(
            "soap", "body wash", "beer", "wine", "alcohol", "drinks",
            "party", "celebration", "gift", "birthday"
    );

    // ─── Main classify method ─────────────────────────────────────────────────

    public AiExpenseResponse classify(String text, Long groupId, String currency) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        String effectiveCurrency = currency != null ? currency : group.getDefaultCurrency();

        List<AiClassifiedItem> classified = openAiClassifier.classify(text);

        // Fall back to the old keyword-based splitter if OpenAI isn't configured or failed
        if (classified == null) {
            classified = classifyWithKeywords(text, effectiveCurrency);
        }

        boolean allClassified = classified.stream()
                .noneMatch(c -> c.getType() == ExpenseType.NEEDS_CONFIRMATION);

        String message = buildSummaryMessage(classified);

        return AiExpenseResponse.builder()
                .originalText(text)
                .items(classified)
                .allClassified(allClassified)
                .message(message)
                .build();
    }

    // ─── Confirm and persist ──────────────────────────────────────────────────

    // Rename your OLD classify() body's for-loop into this method, unchanged:
    private List<AiClassifiedItem> classifyWithKeywords(String text, String currency) {
        String[] rawItems = text.split("(?i),|\\band\\b|;");
        List<AiClassifiedItem> classified = new ArrayList<>();
        for (int i = 0; i < rawItems.length; i++) {
            String item = rawItems[i].trim();
            if (!item.isBlank()) {
                classified.add(classifyItem("item-" + i, item, currency));
            }
        }
        return classified;
    }
    public Map<String, Object> confirmAndCreate(ConfirmExpensesRequest req, String userEmail) {
        int groupExpensesCreated = 0;
        int personalExpensesCreated = 0;

        // Resolve current user ID to use as default payer for group expenses
        Long currentUserId = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();

        for (ConfirmedItem item : req.getItems()) {
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue; // skip items without amount
            }

            if (item.getType() == ExpenseType.GROUP_SHARED) {
                CreateExpenseRequest expReq = new CreateExpenseRequest();
                expReq.setDescription(item.getDescription());
                expReq.setAmount(item.getAmount());
                expReq.setGroupId(req.getGroupId());
                expReq.setPaidByUserId(item.getPaidByUserId() != null ? item.getPaidByUserId() : currentUserId);
                expReq.setSplitType(SplitType.EQUAL);
                expReq.setCategory(item.getCategory());
                expReq.setExpenseDate(item.getExpenseDate() != null ? item.getExpenseDate() : LocalDate.now());
                expReq.setNotes("Added via AI classifier. Original: " + req.getOriginalText());
                expenseService.createExpense(expReq, userEmail);
                groupExpensesCreated++;

            } else if (item.getType() == ExpenseType.PERSONAL) {
                PersonalExpenseRequest pReq = new PersonalExpenseRequest();
                pReq.setDescription(item.getDescription());
                pReq.setAmount(item.getAmount());
                pReq.setCategory(item.getCategory());
                pReq.setExpenseDate(item.getExpenseDate() != null ? item.getExpenseDate() : LocalDate.now());
                pReq.setNotes("AI classified as personal. Original: " + req.getOriginalText());
                personalExpenseService.createFromAi(pReq, userEmail, req.getOriginalText());
                personalExpensesCreated++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("groupExpensesCreated", groupExpensesCreated);
        result.put("personalExpensesCreated", personalExpensesCreated);
        result.put("message", groupExpensesCreated + " group expense(s) and "
                + personalExpensesCreated + " personal expense(s) recorded.");
        return result;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private AiClassifiedItem classifyItem(String itemId, String text, String currency) {
        String lower = text.toLowerCase();

        // Extract amount if present (e.g. "bought shampoo 200" or "₹200 shampoo")
        BigDecimal amount = extractAmount(lower);

        // Derive description: remove numeric/currency tokens
        String description = text.replaceAll("(?i)[₹$€£]?\\s*\\d+(\\.\\d{1,2})?", "").trim();
        if (description.isBlank()) description = text.trim();

        // Classify
        ExpenseType type;
        String reasoning;
        double confidence;
        int splitPercent = 50;

        if (containsAny(lower, PERSONAL_KEYWORDS)) {
            type = ExpenseType.PERSONAL;
            reasoning = "Contains personal-use keyword (e.g. grooming, clothing, subscription, romantic)";
            confidence = 0.90;
        } else if (containsAny(lower, SHARED_KEYWORDS)) {
            type = ExpenseType.GROUP_SHARED;
            reasoning = "Contains shared-expense keyword (e.g. groceries, rent, household item, food)";
            confidence = 0.88;
        } else if (containsAny(lower, NEEDS_CONFIRMATION_KEYWORDS)) {
            type = ExpenseType.NEEDS_CONFIRMATION;
            reasoning = "Could be personal or shared — please confirm";
            confidence = 0.50;
        } else {
            type = ExpenseType.NEEDS_CONFIRMATION;
            reasoning = "No clear keyword match — please confirm the expense type";
            confidence = 0.40;
        }

        String category = inferCategory(lower);

        return AiClassifiedItem.builder()
                .itemId(itemId)
                .description(description)
                .amount(amount)
                .category(category)
                .type(type)
                .suggestedSplitPercent(type == ExpenseType.GROUP_SHARED ? splitPercent : null)
                .reasoning(reasoning)
                .confidence(confidence)
                .build();
    }

    private BigDecimal extractAmount(String text) {
        // Matches patterns like 200, 200.50, ₹200, $45.99
        Pattern pattern = Pattern.compile("(?:[₹$€£])?\\s*(\\d+(?:\\.\\d{1,2})?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private String inferCategory(String lower) {
        if (containsAny(lower, Set.of("food", "dinner", "lunch", "breakfast", "restaurant",
                "pizza", "burger", "chai", "coffee", "snacks"))) return "FOOD";
        if (containsAny(lower, Set.of("grocery", "groceries", "vegetables", "veggies",
                "potato", "onion", "tomato", "milk", "rice", "dal", "eggs"))) return "GROCERIES";
        if (containsAny(lower, Set.of("rent", "maintenance", "repair"))) return "RENT";
        if (containsAny(lower, Set.of("electricity", "internet", "wifi", "gas", "water bill"))) return "UTILITIES";
        if (containsAny(lower, Set.of("medicine", "doctor", "hospital", "pharmacy"))) return "HEALTH";
        if (containsAny(lower, Set.of("shoes", "clothes", "shirt", "jeans", "dress"))) return "SHOPPING";
        if (containsAny(lower, Set.of("netflix", "spotify", "subscription", "gym"))) return "ENTERTAINMENT";
        return "OTHER";
    }

    private String buildSummaryMessage(List<AiClassifiedItem> items) {
        long shared = items.stream().filter(i -> i.getType() == ExpenseType.GROUP_SHARED).count();
        long personal = items.stream().filter(i -> i.getType() == ExpenseType.PERSONAL).count();
        long confirm = items.stream().filter(i -> i.getType() == ExpenseType.NEEDS_CONFIRMATION).count();

        StringBuilder sb = new StringBuilder("Found " + items.size() + " item(s): ");
        if (shared > 0) sb.append(shared).append(" group shared, ");
        if (personal > 0) sb.append(personal).append(" personal, ");
        if (confirm > 0) sb.append(confirm).append(" need your confirmation.");
        else sb.append("ready to save.");
        return sb.toString().replaceAll(", $", ".");
    }
}
