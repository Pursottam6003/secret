package com.expensetracker.service;

import com.expensetracker.model.enums.ExpenseCategory;
import com.expensetracker.repository.ExpenseRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ExpenseRepository expenseRepository;

    /** Daily totals for a date range */
    @Transactional(readOnly = true)
    public List<DataPoint> getDailyTotals(Long groupId, LocalDate from, LocalDate to) {
        List<Object[]> rows = expenseRepository.sumAmountByDayForGroup(groupId, from, to);
        List<DataPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(DataPoint.builder()
                    .label(row[0].toString())
                    .value((BigDecimal) row[1])
                    .build());
        }
        return result;
    }

    /** Weekly totals for a given year */
    @Transactional(readOnly = true)
    public List<DataPoint> getWeeklyTotals(Long groupId, int year) {
        List<Object[]> rows = expenseRepository.sumAmountByWeekForGroup(groupId, year);
        List<DataPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(DataPoint.builder()
                    .label("Week " + row[0])
                    .value((BigDecimal) row[1])
                    .build());
        }
        return result;
    }

    /** Monthly totals for a given year */
    @Transactional(readOnly = true)
    public List<DataPoint> getMonthlyTotals(Long groupId, int year) {
        List<Object[]> rows = expenseRepository.sumAmountByMonthForGroup(groupId, year);
        List<DataPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            int month = ((Number) row[0]).intValue();
            result.add(DataPoint.builder()
                    .label(Month.of(month).name())
                    .value((BigDecimal) row[1])
                    .build());
        }
        return result;
    }

    /** Category breakdown */
    @Transactional(readOnly = true)
    public List<DataPoint> getCategoryBreakdown(Long groupId) {
        List<Object[]> rows = expenseRepository.sumAmountByCategory(groupId);
        List<DataPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(DataPoint.builder()
                    .label(row[0] != null ? row[0].toString() : "OTHER")
                    .value((BigDecimal) row[1])
                    .build());
        }
        return result;
    }

    /** Summary stats for a group */
    @Transactional(readOnly = true)
    public SummaryStats getSummary(Long groupId) {
        LocalDate now = LocalDate.now();

        BigDecimal todayTotal  = safeDecimal(expenseRepository.sumAmountByGroupIdAndDateRange(groupId, now, now));
        BigDecimal weekTotal   = safeDecimal(expenseRepository.sumAmountByGroupIdAndDateRange(
                groupId, now.with(java.time.DayOfWeek.MONDAY), now));
        BigDecimal monthTotal  = safeDecimal(expenseRepository.sumAmountByGroupIdAndDateRange(
                groupId, now.withDayOfMonth(1), now));

        return SummaryStats.builder()
                .todayTotal(todayTotal)
                .thisWeekTotal(weekTotal)
                .thisMonthTotal(monthTotal)
                .build();
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Data @Builder
    public static class DataPoint {
        private String label;
        private BigDecimal value;
    }

    @Data @Builder
    public static class SummaryStats {
        private BigDecimal todayTotal;
        private BigDecimal thisWeekTotal;
        private BigDecimal thisMonthTotal;
    }
}
