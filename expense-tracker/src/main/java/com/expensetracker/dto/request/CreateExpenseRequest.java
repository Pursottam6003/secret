package com.expensetracker.dto.request;

import com.expensetracker.model.enums.ExpenseCategory;
import com.expensetracker.model.enums.SplitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class CreateExpenseRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String currency = "USD";

    @NotNull
    private Long groupId;

    @NotNull
    private Long paidByUserId;

    @NotNull
    private SplitType splitType;

    private ExpenseCategory category = ExpenseCategory.OTHER;

    private LocalDate expenseDate;

    private String notes;

    /**
     * For EXACT split: Map of userId -> exact amount
     * For PERCENTAGE split: Map of userId -> percentage
     * For EQUAL split: List of userIds to split among (use participantIds)
     */
    private Map<Long, BigDecimal> splitDetails;

    /** For EQUAL split: IDs of users involved */
    private List<Long> participantIds;
}
