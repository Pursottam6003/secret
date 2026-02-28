package com.expensetracker.controller;

import com.expensetracker.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/group/{groupId}/daily")
    public ResponseEntity<List<AnalyticsService.DataPoint>> daily(
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.getDailyTotals(groupId, from, to));
    }

    @GetMapping("/group/{groupId}/weekly")
    public ResponseEntity<List<AnalyticsService.DataPoint>> weekly(
            @PathVariable Long groupId,
            @RequestParam(required = false) Integer year) {
        int resolvedYear = (year != null) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(analyticsService.getWeeklyTotals(groupId, resolvedYear));
    }

    @GetMapping("/group/{groupId}/monthly")
    public ResponseEntity<List<AnalyticsService.DataPoint>> monthly(
            @PathVariable Long groupId,
            @RequestParam(required = false) Integer year) {
        int resolvedYear = (year != null) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(analyticsService.getMonthlyTotals(groupId, resolvedYear));
    }

    @GetMapping("/group/{groupId}/categories")
    public ResponseEntity<List<AnalyticsService.DataPoint>> categories(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(analyticsService.getCategoryBreakdown(groupId));
    }

    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<AnalyticsService.SummaryStats> summary(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(analyticsService.getSummary(groupId));
    }
}
