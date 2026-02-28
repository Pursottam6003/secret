package com.expensetracker.controller;

import com.expensetracker.dto.request.PersonalExpenseRequest;
import com.expensetracker.model.PersonalExpense;
import com.expensetracker.service.PersonalExpenseService;
import com.expensetracker.service.PersonalExpenseService.PersonalSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/personal-expenses")
@RequiredArgsConstructor
public class PersonalExpenseController {

    private final PersonalExpenseService personalExpenseService;

    @GetMapping
    public ResponseEntity<List<PersonalExpense>> getMyExpenses(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(personalExpenseService.getMyExpenses(user.getUsername()));
    }

    @GetMapping("/summary")
    public ResponseEntity<PersonalSummary> getSummary(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(personalExpenseService.getMySummary(user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<PersonalExpense> create(
            @RequestBody PersonalExpenseRequest req,
            @AuthenticationPrincipal UserDetails user) {
        PersonalExpense created = personalExpenseService.create(req, user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        personalExpenseService.delete(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
