package com.expensetracker.controller;

import com.expensetracker.dto.request.SettleUpRequest;
import com.expensetracker.dto.response.GroupBalanceResponse;
import com.expensetracker.model.Settlement;
import com.expensetracker.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /** Get net balances + optimal settlement plan for a group */
    @GetMapping("/group/{groupId}/balances")
    public ResponseEntity<GroupBalanceResponse> getBalances(@PathVariable Long groupId) {
        return ResponseEntity.ok(settlementService.getGroupBalances(groupId));
    }

    /** Record a real payment between two users */
    @PostMapping
    public ResponseEntity<Settlement> settle(@Valid @RequestBody SettleUpRequest req,
                                             @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(settlementService.recordSettlement(req, user.getUsername()));
    }

    /** List all settlements for a group */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Settlement>> getSettlements(@PathVariable Long groupId) {
        return ResponseEntity.ok(settlementService.getGroupSettlements(groupId));
    }

    /** Cancel a pending settlement */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Settlement> cancel(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(settlementService.cancelSettlement(id, user.getUsername()));
    }
}
