package com.expensetracker.dto.request;

import com.expensetracker.model.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PersonalExpenseRequest {
    private String description;
    private BigDecimal amount;
    private String currency;
    private ExpenseCategory category;
    private LocalDate expenseDate;
    private String notes;
}
