package com.expensetracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupBalanceResponse {

    private Long groupId;
    private String groupName;

    /** Net balance per user (positive = owed to user, negative = user owes) */
    private List<UserBalance> balances;

    /** Optimal settlement plan (minimized transactions) */
    private List<SettlementSuggestion> suggestions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserBalance {
        private Long userId;
        private String userName;
        private BigDecimal netBalance;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SettlementSuggestion {
        private Long fromUserId;
        private String fromUserName;
        private Long toUserId;
        private String toUserName;
        private BigDecimal amount;
        private String currency;
    }
}
