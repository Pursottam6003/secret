package com.expensetracker.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AiExpenseResponse {

    /** The original user input text */
    private String originalText;

    /** Classified items extracted from the text */
    private List<AiClassifiedItem> items;

    /** True if all items were definitively classified (no NEEDS_CONFIRMATION) */
    private boolean allClassified;

    /** Summary message shown to user */
    private String message;

    @Data
    @Builder
    public static class AiClassifiedItem {
        /** Unique ID for this item (used during confirmation) */
        private String itemId;
        /** Human-readable description */
        private String description;
        /** Suggested amount (may be null if not parseable from text) */
        private BigDecimal amount;
        /** Expense category */
        private String category;
        /** GROUP_SHARED, PERSONAL, or NEEDS_CONFIRMATION */
        private ExpenseType type;
        /** Suggested split percentage for GROUP_SHARED (default 50 for equal split) */
        private Integer suggestedSplitPercent;
        /** Why the AI classified it this way */
        private String reasoning;
        /** 0-1 confidence score */
        private double confidence;
    }

    public enum ExpenseType {
        /** Should be added to the group expense (split among members) */
        GROUP_SHARED,
        /** Personal expense — stored for individual analytics only */
        PERSONAL,
        /** AI is uncertain — show user a prompt to decide */
        NEEDS_CONFIRMATION
    }
}
