package com.expensetracker.service;

import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.model.*;
import com.expensetracker.model.enums.SplitType;
import com.expensetracker.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public Expense createExpense(CreateExpenseRequest req, String creatorEmail) {
        Group group = groupRepository.findById(req.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        User paidBy = userRepository.findById(req.getPaidByUserId())
                .orElseThrow(() -> new IllegalArgumentException("Payer not found"));

        assertMember(group.getId(), creatorEmail);

        Expense expense = Expense.builder()
                .description(req.getDescription())
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : group.getDefaultCurrency())
                .splitType(req.getSplitType())
                .category(req.getCategory())
                .expenseDate(req.getExpenseDate() != null ? req.getExpenseDate() : LocalDate.now())
                .notes(req.getNotes())
                .group(group)
                .paidBy(paidBy)
                .build();

        List<ExpenseSplit> splits = buildSplits(expense, req, group);
        expense.getSplits().addAll(splits);

        return expenseRepository.save(expense);
    }

    @Transactional
    public void deleteExpense(Long expenseId, String requesterEmail) {
        Expense expense = getExpense(expenseId);
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!expense.getPaidBy().getId().equals(requester.getId())) {
            throw new SecurityException("Only the payer can delete this expense");
        }
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public List<Expense> getGroupExpenses(Long groupId, String userEmail) {
        assertMember(groupId, userEmail);
        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
    }

    @Transactional(readOnly = true)
    public List<Expense> getUnsettledExpenses(Long groupId, String userEmail) {
        assertMember(groupId, userEmail);
        return expenseRepository.findByGroupIdAndSettledFalseOrderByExpenseDateDesc(groupId);
    }

    @Transactional(readOnly = true)
    public Expense getExpense(Long expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + expenseId));
    }

    // ─── Split builders ───────────────────────────────────────────────────────

    private List<ExpenseSplit> buildSplits(Expense expense, CreateExpenseRequest req, Group group) {
        return switch (req.getSplitType()) {
            case EQUAL      -> buildEqualSplits(expense, req, group);
            case EXACT      -> buildExactSplits(expense, req);
            case PERCENTAGE -> buildPercentageSplits(expense, req);
        };
    }

    private List<ExpenseSplit> buildEqualSplits(Expense expense, CreateExpenseRequest req, Group group) {
        List<Long> participantIds = req.getParticipantIds();
        if (participantIds == null || participantIds.isEmpty()) {
            // Default: all group members
            participantIds = groupMemberRepository.findByGroupId(group.getId())
                    .stream().map(m -> m.getUser().getId()).toList();
        }

        int count = participantIds.size();
        BigDecimal share = expense.getAmount()
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        return participantIds.stream().map(userId -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            return ExpenseSplit.builder()
                    .expense(expense)
                    .user(user)
                    .amount(share)
                    .paid(userId.equals(expense.getPaidBy().getId()))
                    .build();
        }).toList();
    }

    private List<ExpenseSplit> buildExactSplits(Expense expense, CreateExpenseRequest req) {
        Map<Long, BigDecimal> details = req.getSplitDetails();
        if (details == null || details.isEmpty()) {
            throw new IllegalArgumentException("splitDetails required for EXACT split");
        }
        BigDecimal total = details.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(expense.getAmount()) != 0) {
            throw new IllegalArgumentException("Exact splits must sum to the total expense amount");
        }
        return details.entrySet().stream().map(entry -> {
            User user = userRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + entry.getKey()));
            return ExpenseSplit.builder()
                    .expense(expense)
                    .user(user)
                    .amount(entry.getValue())
                    .paid(entry.getKey().equals(expense.getPaidBy().getId()))
                    .build();
        }).toList();
    }

    private List<ExpenseSplit> buildPercentageSplits(Expense expense, CreateExpenseRequest req) {
        Map<Long, BigDecimal> details = req.getSplitDetails();
        if (details == null || details.isEmpty()) {
            throw new IllegalArgumentException("splitDetails required for PERCENTAGE split");
        }
        BigDecimal totalPct = details.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPct.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("Percentages must sum to 100");
        }
        return details.entrySet().stream().map(entry -> {
            User user = userRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + entry.getKey()));
            BigDecimal amount = expense.getAmount()
                    .multiply(entry.getValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return ExpenseSplit.builder()
                    .expense(expense)
                    .user(user)
                    .amount(amount)
                    .percentage(entry.getValue())
                    .paid(entry.getKey().equals(expense.getPaidBy().getId()))
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public MyGroupStats getMyGroupStats(Long groupId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        BigDecimal totalIPaid = safe(expenseRepository.sumAmountPaidByUserInGroup(groupId, user.getId()));
        BigDecimal myShare    = safe(expenseRepository.sumUserShareInGroup(groupId, user.getId()));
        BigDecimal netBalance = totalIPaid.subtract(myShare);
        return MyGroupStats.builder()
                .totalIPaid(totalIPaid)
                .myShare(myShare)
                .netBalance(netBalance)
                .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Data @Builder
    public static class MyGroupStats {
        private BigDecimal totalIPaid;
        private BigDecimal myShare;
        private BigDecimal netBalance;
    }

    private void assertMember(Long groupId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
            throw new SecurityException("User is not a member of this group");
        }
    }
}
