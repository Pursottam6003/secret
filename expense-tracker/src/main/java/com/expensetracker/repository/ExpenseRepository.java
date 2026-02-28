package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import com.expensetracker.model.enums.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);

    List<Expense> findByGroupIdAndSettledFalseOrderByExpenseDateDesc(Long groupId);

    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId AND e.expenseDate BETWEEN :from AND :to ORDER BY e.expenseDate DESC")
    List<Expense> findByGroupIdAndDateRange(@Param("groupId") Long groupId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("SELECT e FROM Expense e JOIN e.splits s WHERE s.user.id = :userId AND e.group.id = :groupId AND s.paid = false")
    List<Expense> findUnpaidExpensesByUserAndGroup(@Param("userId") Long userId,
                                                   @Param("groupId") Long groupId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.expenseDate BETWEEN :from AND :to")
    BigDecimal sumAmountByGroupIdAndDateRange(@Param("groupId") Long groupId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId GROUP BY e.category")
    List<Object[]> sumAmountByCategory(@Param("groupId") Long groupId);

    @Query("SELECT e.expenseDate, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.expenseDate BETWEEN :from AND :to GROUP BY e.expenseDate ORDER BY e.expenseDate")
    List<Object[]> sumAmountByDayForGroup(@Param("groupId") Long groupId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("SELECT FUNCTION('WEEK', e.expenseDate), COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND FUNCTION('YEAR', e.expenseDate) = :year GROUP BY FUNCTION('WEEK', e.expenseDate) ORDER BY FUNCTION('WEEK', e.expenseDate)")
    List<Object[]> sumAmountByWeekForGroup(@Param("groupId") Long groupId, @Param("year") int year);

    @Query("SELECT FUNCTION('MONTH', e.expenseDate), COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND FUNCTION('YEAR', e.expenseDate) = :year GROUP BY FUNCTION('MONTH', e.expenseDate) ORDER BY FUNCTION('MONTH', e.expenseDate)")
    List<Object[]> sumAmountByMonthForGroup(@Param("groupId") Long groupId, @Param("year") int year);

    @Query("SELECT e FROM Expense e JOIN e.splits s WHERE s.user.id = :userId ORDER BY e.expenseDate DESC")
    List<Expense> findAllExpensesInvolvingUser(@Param("userId") Long userId);

    /** Total amount paid by a user in a group */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.paidBy.id = :userId")
    BigDecimal sumAmountPaidByUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** Total share owed by a user in a group (sum of their splits) */
    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM ExpenseSplit s WHERE s.expense.group.id = :groupId AND s.user.id = :userId")
    BigDecimal sumUserShareInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
