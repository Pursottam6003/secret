package com.expensetracker.service;

import com.expensetracker.dto.request.SettleUpRequest;
import com.expensetracker.dto.response.GroupBalanceResponse;
import com.expensetracker.model.*;
import com.expensetracker.model.enums.SettlementStatus;
import com.expensetracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core service for balance calculation and the Splitwise-style
 * "Minimize Cash Flow" settlement algorithm.
 *
 * Algorithm complexity: O(n log n) per settlement cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final ExpenseRepository   expenseRepository;
    private final GroupRepository     groupRepository;
    private final UserRepository      userRepository;
    private final GroupMemberRepository groupMemberRepository;

    // ─── Balance Calculation ──────────────────────────────────────────────────

    /**
     * Returns the net balance for every group member.
     * Positive  → the user is owed money by others.
     * Negative  → the user owes money to others.
     */
    @Transactional(readOnly = true)
    public GroupBalanceResponse getGroupBalances(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<GroupMember> members = groupMemberRepository.findByGroupIdWithUser(groupId);
        Map<Long, BigDecimal> netBalance = new HashMap<>();
        Map<Long, String>     nameMap    = new HashMap<>();

        for (GroupMember gm : members) {
            netBalance.put(gm.getUser().getId(), BigDecimal.ZERO);
            nameMap.put(gm.getUser().getId(), gm.getUser().getName());
        }

        // For every expense in the group:
        //   payer gets +amount  (they funded it)
        //   each participant gets -(their share)
        List<Expense> expenses = expenseRepository.findByGroupIdAndSettledFalseOrderByExpenseDateDesc(groupId);
        for (Expense expense : expenses) {
            Long payerId = expense.getPaidBy().getId();
            netBalance.merge(payerId, expense.getAmount(), BigDecimal::add);

            for (ExpenseSplit split : expense.getSplits()) {
                Long splitUserId = split.getUser().getId();
                netBalance.merge(splitUserId, split.getAmount().negate(), BigDecimal::add);
            }
        }

        // Factor in already-completed settlements
        List<Settlement> settlements = settlementRepository.findByGroupIdAndStatus(groupId, SettlementStatus.COMPLETED);
        for (Settlement s : settlements) {
            // Payer sent money → reduce their debt (increase balance)
            netBalance.merge(s.getPayer().getId(), s.getAmount(), BigDecimal::add);
            // Receiver got money → reduce their credit
            netBalance.merge(s.getReceiver().getId(), s.getAmount().negate(), BigDecimal::add);
        }

        // Build response balances
        List<GroupBalanceResponse.UserBalance> balances = netBalance.entrySet().stream()
                .map(e -> GroupBalanceResponse.UserBalance.builder()
                        .userId(e.getKey())
                        .userName(nameMap.getOrDefault(e.getKey(), "Unknown"))
                        .netBalance(e.getValue().setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();

        // Run the minimize-cash-flow algorithm
        List<GroupBalanceResponse.SettlementSuggestion> suggestions =
                minimizeCashFlow(netBalance, nameMap, group.getDefaultCurrency());

        return GroupBalanceResponse.builder()
                .groupId(groupId)
                .groupName(group.getName())
                .balances(balances)
                .suggestions(suggestions)
                .build();
    }

    // ─── Minimize Cash Flow (Splitwise Algorithm) ─────────────────────────────

    /**
     * Greedy algorithm to minimize the number of transactions needed to settle up.
     *
     * Steps:
     *  1. Separate users into creditors (balance > 0) and debtors (balance < 0).
     *  2. Use two max-heaps (by absolute value).
     *  3. Each iteration: match the largest creditor with the largest debtor.
     *     - Transfer min(|creditor|, |debtor|).
     *     - If remainders exist, keep in heap.
     *  4. Repeat until all balances are zero.
     */
    private List<GroupBalanceResponse.SettlementSuggestion> minimizeCashFlow(
            Map<Long, BigDecimal> netBalance,
            Map<Long, String>     nameMap,
            String                currency) {

        // Max-heaps by absolute balance value
        PriorityQueue<long[]> creditors = new PriorityQueue<>(
                (a, b) -> Long.compare(b[1], a[1])); // [userId, balance_in_cents]
        PriorityQueue<long[]> debtors   = new PriorityQueue<>(
                (a, b) -> Long.compare(b[1], a[1])); // [userId, abs(balance)_in_cents]

        // Work in cents to avoid floating-point drift
        for (var entry : netBalance.entrySet()) {
            long cents = entry.getValue().multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            if (cents > 0) {
                creditors.offer(new long[]{entry.getKey(), cents});
            } else if (cents < 0) {
                debtors.offer(new long[]{entry.getKey(), -cents});
            }
        }

        List<GroupBalanceResponse.SettlementSuggestion> suggestions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            long[] creditor = creditors.poll();
            long[] debtor   = debtors.poll();

            long transfer = Math.min(creditor[1], debtor[1]);

            BigDecimal amount = BigDecimal.valueOf(transfer)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            suggestions.add(GroupBalanceResponse.SettlementSuggestion.builder()
                    .fromUserId(debtor[0])
                    .fromUserName(nameMap.getOrDefault(debtor[0], "User " + debtor[0]))
                    .toUserId(creditor[0])
                    .toUserName(nameMap.getOrDefault(creditor[0], "User " + creditor[0]))
                    .amount(amount)
                    .currency(currency)
                    .build());

            long creditorRemainder = creditor[1] - transfer;
            long debtorRemainder   = debtor[1]   - transfer;

            if (creditorRemainder > 0) creditors.offer(new long[]{creditor[0], creditorRemainder});
            if (debtorRemainder   > 0) debtors.offer(new long[]{debtor[0],   debtorRemainder});
        }

        return suggestions;
    }

    // ─── Record Settlement ─────────────────────────────────────────────────────

    @Transactional
    public Settlement recordSettlement(SettleUpRequest req, String requesterEmail) {
        Group group    = groupRepository.findById(req.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        User payer     = userRepository.findById(req.getPayerId())
                .orElseThrow(() -> new IllegalArgumentException("Payer not found"));
        User receiver  = userRepository.findById(req.getReceiverId())
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

        Settlement settlement = Settlement.builder()
                .group(group)
                .payer(payer)
                .receiver(receiver)
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : group.getDefaultCurrency())
                .notes(req.getNotes())
                .paymentReference(req.getPaymentReference())
                .status(SettlementStatus.COMPLETED)
                .settledAt(LocalDateTime.now())
                .build();

        return settlementRepository.save(settlement);
    }

    @Transactional
    public Settlement cancelSettlement(Long settlementId, String requesterEmail) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found"));
        settlement.setStatus(SettlementStatus.CANCELLED);
        return settlementRepository.save(settlement);
    }

    @Transactional(readOnly = true)
    public List<Settlement> getGroupSettlements(Long groupId) {
        return settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }
}
