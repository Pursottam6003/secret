package com.expensetracker.controller;

import com.expensetracker.model.Group;
import com.expensetracker.service.GroupService;
import com.expensetracker.service.SettlementService;
import com.expensetracker.service.AnalyticsService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.PersonalExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final GroupService groupService;
    private final SettlementService settlementService;
    private final AnalyticsService analyticsService;
    private final ExpenseService expenseService;
    private final PersonalExpenseService personalExpenseService;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String register() {
        return "auth/register";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal UserDetails user) {
        List<Group> groups = groupService.getUserGroups(user.getUsername());
        model.addAttribute("groups", groups);
        model.addAttribute("currentUser", user.getUsername());
        return "dashboard";
    }

    @GetMapping("/groups/{id}")
    public String groupDetail(@PathVariable Long id, Model model,
                              @AuthenticationPrincipal UserDetails user) {
        Group group = groupService.getGroup(id);
        var members = groupService.getMembers(id);
        var balances = settlementService.getGroupBalances(id);
        var summary  = analyticsService.getSummary(id);

        var myStats = expenseService.getMyGroupStats(id, user.getUsername());

        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("balances", balances);
        model.addAttribute("summary", summary);
        model.addAttribute("myStats", myStats);
        model.addAttribute("currentUser", user.getUsername());
        return "group/detail";
    }

    @GetMapping("/groups/{id}/analytics")
    public String analytics(@PathVariable Long id, Model model,
                             @AuthenticationPrincipal UserDetails user) {
        Group group = groupService.getGroup(id);
        int year = LocalDate.now().getYear();
        System.out.println("Analytics for group: " + group + ", year: " + year);
        System.out.println("Summary: " + analyticsService.getSummary(id));

        model.addAttribute("group", group);
        model.addAttribute("monthlyData", analyticsService.getMonthlyTotals(id, year));
        model.addAttribute("weeklyData",  analyticsService.getWeeklyTotals(id, year));
        model.addAttribute("categoryData", analyticsService.getCategoryBreakdown(id));
        model.addAttribute("summary",  analyticsService.getSummary(id));
        model.addAttribute("currentUser", user.getUsername());
        return "group/analytics";
    }

    @GetMapping("/personal-expenses")
    public String personalExpenses(Model model, @AuthenticationPrincipal UserDetails user) {
        var summary  = personalExpenseService.getMySummary(user.getUsername());
        var expenses = personalExpenseService.getMyExpenses(user.getUsername());
        model.addAttribute("summary", summary);
        model.addAttribute("expenses", expenses);
        model.addAttribute("currentUser", user.getUsername());
        return "personal-expenses";
    }

    @GetMapping("/groups/{id}/settlements")
    public String settlements(@PathVariable Long id, Model model,
                              @AuthenticationPrincipal UserDetails user) {
        Group group = groupService.getGroup(id);
        var balances = settlementService.getGroupBalances(id);
        var history  = settlementService.getGroupSettlements(id);

        model.addAttribute("group", group);
        model.addAttribute("balances", balances);
        model.addAttribute("history", history);
        model.addAttribute("currentUser", user.getUsername());
        return "group/settlements";
    }
}
