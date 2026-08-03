package com.expensetracker.controller;

import com.expensetracker.model.GroupInvite;
import com.expensetracker.model.User;
import com.expensetracker.service.GroupInviteService;
import com.expensetracker.service.GroupMemberService;
import com.expensetracker.service.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
@Slf4j
public class InviteController {
    private final GroupInviteService inviteService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    // GET /invite/{token} - shows the accept page
    @GetMapping("/invite/{token}")
    public String showInvitePage(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails user,
            Model model) {
        try {
            GroupInvite invite = inviteService.getInviteByToken(token); // Add this method to service
            model.addAttribute("invite", invite);
            model.addAttribute("token", token);
            model.addAttribute("user", user);
            return "accept-invite";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "accept-invite";
        }
    }

    // POST /invite/accept - actually accept the invite
    @PostMapping("/invite/accept")
    public String acceptInvite(
            @RequestParam String token,
            @AuthenticationPrincipal UserDetails user,
            RedirectAttributes redirectAttrs) {
        if (user == null) {
            redirectAttrs.addFlashAttribute("error", "Please log in to accept this invite");
            return "redirect:/login?redirect=" + URLEncoder.encode("/invite/" + token, StandardCharsets.UTF_8);
        }
        try {
            GroupInvite invite = inviteService.acceptInvite(token);
            User currentUser = userService.getUserByEmail(user.getUsername());
            groupMemberService.addMember(invite.getGroup().getId(), currentUser.getId());

            redirectAttrs.addFlashAttribute("success",
                    "Successfully joined " + invite.getGroup().getName());
            return "redirect:/groups/" + invite.getGroup().getId();
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }
}
