package com.expensetracker.repository;

import com.expensetracker.model.PersonalExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PersonalExpenseRepository extends JpaRepository<PersonalExpense, Long> {

    List<PersonalExpense> findByUserIdOrderByExpenseDateDesc(Long userId);

    List<PersonalExpense> findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            Long userId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PersonalExpense p WHERE p.user.id = :userId AND p.expenseDate BETWEEN :from AND :to")
    BigDecimal sumAmountByUserAndDateRange(@Param("userId") Long userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("SELECT p.category, COALESCE(SUM(p.amount), 0) FROM PersonalExpense p WHERE p.user.id = :userId GROUP BY p.category")
    List<Object[]> sumAmountByCategoryForUser(@Param("userId") Long userId);

    @Query(value = "SELECT EXTRACT(MONTH FROM p.expense_date) AS month, COALESCE(SUM(p.amount), 0) AS total " +
            "FROM personal_expenses p WHERE p.user_id = :userId AND EXTRACT(YEAR FROM p.expense_date) = :year " +
            "GROUP BY EXTRACT(MONTH FROM p.expense_date) ORDER BY EXTRACT(MONTH FROM p.expense_date)",
            nativeQuery = true)
    List<Object[]> sumAmountByMonthForUser(@Param("userId") Long userId, @Param("year") int year);
}
