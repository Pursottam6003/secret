package com.expensetracker.dto.request;

import com.expensetracker.dto.response.AiExpenseResponse.ExpenseType;
import com.expensetracker.model.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ConfirmExpensesRequest {
    private Long groupId;
    private String originalText;
    private List<ConfirmedItem> items;

    @Data
    public static class ConfirmedItem {
        private String itemId;
        private String description;
        private BigDecimal amount;
        private ExpenseCategory category;
        /** Final decision: GROUP_SHARED or PERSONAL */
        private ExpenseType type;
        /** Split % if GROUP_SHARED (50 means equal split between payer and rest) */
        private Integer splitPercent;
        private LocalDate expenseDate;
        /** User ID who paid (for group expenses) */
        private Long paidByUserId;
    }
}
