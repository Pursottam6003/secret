package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_splits",
    uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"}))
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Amount this user owes for this expense */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Optional: percentage share (for PERCENTAGE split type) */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "is_paid")
    @Builder.Default
    private boolean paid = false;
}
