package com.expensetracker.service;

import com.expensetracker.model.Group;
import com.expensetracker.model.GroupInvite;
import com.expensetracker.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import com.expensetracker.repository.GroupInviteRepository;


import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupInviteService {
    private final GroupInviteRepository inviteRepository;
    private final EmailService emailService;
    private final GroupService groupService;
    private final UserService userService;
    private final GroupMemberService groupMemberService;

    public void inviteUserToGroup(Long groupId, String email, Long invitedByUserId) {
        Group group = groupService.getGroup(groupId);
        User invitedBy = userService.getUserById(invitedByUserId);

        // Check if already invited
        if (inviteRepository.existsByGroupIdAndEmail(groupId, email)) {
            throw new IllegalArgumentException("User already invited to this group");
        }

        String token = UUID.randomUUID().toString();
        GroupInvite invite = GroupInvite.builder()
                .group(group)
                .email(email)
                .token(token)
                .invitedBy(invitedBy)
                .build();
        inviteRepository.save(invite);

        emailService.sendGroupInvite(email, group, token, invitedBy);
    }

    public GroupInvite acceptInvite(String token) {
        GroupInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (invite.isAccepted()) {
            throw new IllegalArgumentException("Invite already accepted");
        }

        if (LocalDateTime.now().isAfter(invite.getCreatedAt().plusDays(30))) {
            throw new IllegalArgumentException("Invite expired");
        }

        invite.setAccepted(true);
        invite.setAcceptedAt(LocalDateTime.now());
        return inviteRepository.save(invite);
    }
    public GroupInvite getInviteByToken(String token) {
        return inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite link"));
    }
}