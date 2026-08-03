package com.expensetracker.controller;

import com.expensetracker.model.User;
import com.expensetracker.service.GroupInviteService;
import com.expensetracker.service.GroupMemberService;
import com.expensetracker.service.UserService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;


@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupInviteController {
    private final GroupInviteService inviteService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    @PostMapping("/{id}/invite")
    public ResponseEntity<String> inviteUser(
            @PathVariable Long id,
            @RequestBody InviteRequest req,
            @AuthenticationPrincipal UserDetails user) {
        User currentUser = userService.getUserByEmail(user.getUsername());
        inviteService.inviteUserToGroup(id, req.getEmail(), currentUser.getId());
        return ResponseEntity.ok("Invite sent to " + req.getEmail());
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addOrInviteMember(
            @PathVariable Long id,
            @RequestParam String email,
            @AuthenticationPrincipal UserDetails user) {

        User currentUser = userService.getUserByEmail(user.getUsername());

        try {
            String result = groupMemberService.addOrInviteMember(id, email, currentUser.getId(), inviteService);

            if ("added".equals(result)) {
                return ResponseEntity.ok(Map.of("status", "added", "message", "Member added to group"));
            } else {
                return ResponseEntity.ok(Map.of("status", "invited", "message", "Invite email sent to " + email));
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

@Data
class InviteRequest {
    @Email
    @NotBlank
    private String email;
}