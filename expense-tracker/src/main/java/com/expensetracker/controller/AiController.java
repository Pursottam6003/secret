package com.expensetracker.controller;

import com.expensetracker.dto.request.AiExpenseRequest;
import com.expensetracker.dto.request.ConfirmExpensesRequest;
import com.expensetracker.dto.response.AiExpenseResponse;
import com.expensetracker.service.AiExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiExpenseService aiExpenseService;

    /**
     * Classify natural-language input into GROUP_SHARED / PERSONAL / NEEDS_CONFIRMATION items.
     * POST /api/ai/classify
     */
    @PostMapping("/classify")
    public ResponseEntity<AiExpenseResponse> classify(
            @RequestBody AiExpenseRequest req,
            @AuthenticationPrincipal UserDetails user) {

        if (req.getText() == null || req.getText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AiExpenseResponse response = aiExpenseService.classify(
                req.getText(), req.getGroupId(), req.getCurrency());
        return ResponseEntity.ok(response);
    }

    /**
     * Confirm classified items and persist them as group expenses or personal expenses.
     * POST /api/ai/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(
            @RequestBody ConfirmExpensesRequest req,
            @AuthenticationPrincipal UserDetails user) {

        Map<String, Object> result = aiExpenseService.confirmAndCreate(req, user.getUsername());
        return ResponseEntity.ok(result);
    }
}
