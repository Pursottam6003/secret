package com.expensetracker.controller;

import com.expensetracker.dto.request.CreateGroupRequest;
import com.expensetracker.model.Group;
import com.expensetracker.model.GroupMember;
import com.expensetracker.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<Group> createGroup(@Valid @RequestBody CreateGroupRequest req,
                                             @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(groupService.createGroup(req, user.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<Group>> myGroups(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(groupService.getUserGroups(user.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroup(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(groupService.getGroup(id));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<GroupMember>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getMembers(id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long id,
                                          @RequestParam String email,
                                          @AuthenticationPrincipal UserDetails user) {
        groupService.addMemberByEmail(id, email, user.getUsername());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal UserDetails user) {
        groupService.removeMember(id, userId, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id,
                                            @AuthenticationPrincipal UserDetails user) {
        groupService.deleteGroup(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
