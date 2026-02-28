package com.expensetracker.dto.request;

import lombok.Data;

@Data
public class AiExpenseRequest {
    /** Natural-language text e.g. "bought potatoes, shampoo, paid for dinner with gf" */
    private String text;
    /** The group context for classification */
    private Long groupId;
    /** Currency to use for suggestions (falls back to group default) */
    private String currency;
}
