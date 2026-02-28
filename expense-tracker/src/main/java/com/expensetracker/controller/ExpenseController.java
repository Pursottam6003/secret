package com.expensetracker.controller;

import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<Expense> createExpense(@Valid @RequestBody CreateExpenseRequest req,
                                                 @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(expenseService.createExpense(req, user.getUsername()));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Expense>> getGroupExpenses(@PathVariable Long groupId,
                                                          @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(expenseService.getGroupExpenses(groupId, user.getUsername()));
    }

    @GetMapping("/group/{groupId}/unsettled")
    public ResponseEntity<List<Expense>> getUnsettledExpenses(@PathVariable Long groupId,
                                                               @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(expenseService.getUnsettledExpenses(groupId, user.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpense(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpense(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id,
                                              @AuthenticationPrincipal UserDetails user) {
        expenseService.deleteExpense(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
