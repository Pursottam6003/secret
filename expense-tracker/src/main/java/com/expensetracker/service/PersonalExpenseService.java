package com.expensetracker.service;

import com.expensetracker.dto.request.PersonalExpenseRequest;
import com.expensetracker.model.PersonalExpense;
import com.expensetracker.model.User;
import com.expensetracker.repository.PersonalExpenseRepository;
import com.expensetracker.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalExpenseService {

    private final PersonalExpenseRepository personalExpenseRepository;
    private final UserRepository userRepository;

    @Transactional
    public PersonalExpense create(PersonalExpenseRequest req, String userEmail) {
        User user = getUser(userEmail);
        return personalExpenseRepository.save(buildExpense(req, user, "MANUAL", null));
    }

    @Transactional
    public PersonalExpense createFromAi(PersonalExpenseRequest req, String userEmail, String originalText) {
        User user = getUser(userEmail);
        return personalExpenseRepository.save(buildExpense(req, user, "AI_CLASSIFIED", originalText));
    }

    @Transactional(readOnly = true)
    public List<PersonalExpense> getMyExpenses(String userEmail) {
        User user = getUser(userEmail);
        return personalExpenseRepository.findByUserIdOrderByExpenseDateDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public PersonalSummary getMySummary(String userEmail) {
        User user = getUser(userEmail);
        LocalDate now = LocalDate.now();

        BigDecimal todayTotal = safe(personalExpenseRepository.sumAmountByUserAndDateRange(user.getId(), now, now));
        BigDecimal weekTotal  = safe(personalExpenseRepository.sumAmountByUserAndDateRange(
                user.getId(), now.with(java.time.DayOfWeek.MONDAY), now));
        BigDecimal monthTotal = safe(personalExpenseRepository.sumAmountByUserAndDateRange(
                user.getId(), now.withDayOfMonth(1), now));

        List<CategoryBreakdown> categories = new ArrayList<>();
        for (Object[] row : personalExpenseRepository.sumAmountByCategoryForUser(user.getId())) {
            categories.add(CategoryBreakdown.builder()
                    .category(row[0] != null ? row[0].toString() : "OTHER")
                    .total((BigDecimal) row[1])
                    .build());
        }

        List<MonthlyTotal> monthly = new ArrayList<>();
        int year = now.getYear();
        for (Object[] row : personalExpenseRepository.sumAmountByMonthForUser(user.getId(), year)) {
            int month = ((Number) row[0]).intValue();
            monthly.add(MonthlyTotal.builder()
                    .month(Month.of(month).name())
                    .total((BigDecimal) row[1])
                    .build());
        }

        return PersonalSummary.builder()
                .todayTotal(todayTotal)
                .thisWeekTotal(weekTotal)
                .thisMonthTotal(monthTotal)
                .categoryBreakdown(categories)
                .monthlyTotals(monthly)
                .build();
    }

    @Transactional
    public void delete(Long expenseId, String userEmail) {
        User user = getUser(userEmail);
        PersonalExpense expense = personalExpenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Personal expense not found"));
        if (!expense.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Cannot delete another user's expense");
        }
        personalExpenseRepository.delete(expense);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private PersonalExpense buildExpense(PersonalExpenseRequest req, User user, String source, String originalText) {
        return PersonalExpense.builder()
                .user(user)
                .description(req.getDescription())
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : user.getPreferredCurrency())
                .category(req.getCategory())
                .expenseDate(req.getExpenseDate() != null ? req.getExpenseDate() : LocalDate.now())
                .notes(req.getNotes())
                .source(source)
                .originalAiText(originalText)
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ─── Inner response types ─────────────────────────────────────────────────

    @Data @Builder
    public static class PersonalSummary {
        private BigDecimal todayTotal;
        private BigDecimal thisWeekTotal;
        private BigDecimal thisMonthTotal;
        private List<CategoryBreakdown> categoryBreakdown;
        private List<MonthlyTotal> monthlyTotals;
    }

    @Data @Builder
    public static class CategoryBreakdown {
        private String category;
        private BigDecimal total;
    }

    @Data @Builder
    public static class MonthlyTotal {
        private String month;
        private BigDecimal total;
    }
}
