package com.expensetracker.service;

import com.expensetracker.dto.request.CreateGroupRequest;
import com.expensetracker.model.Group;
import com.expensetracker.model.GroupMember;
import com.expensetracker.model.User;
import com.expensetracker.repository.GroupMemberRepository;
import com.expensetracker.repository.GroupRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public Group createGroup(CreateGroupRequest req, String creatorEmail) {
        User creator = findUser(creatorEmail);

        Group group = Group.builder()
                .name(req.getName())
                .description(req.getDescription())
                .defaultCurrency(req.getDefaultCurrency() != null ? req.getDefaultCurrency() : "USD")
                .createdBy(creator)
                .build();
        groupRepository.save(group);

        // Add creator as ADMIN
        addMember(group, creator, GroupMember.Role.ADMIN);

        // Add invited members
        if (req.getMemberEmails() != null) {
            for (String email : req.getMemberEmails()) {
                userRepository.findByEmail(email).ifPresent(u -> addMember(group, u, GroupMember.Role.MEMBER));
            }
        }
        return group;
    }

    @Transactional
    public void addMemberByEmail(Long groupId, String email, String requesterEmail) {
        Group group = getGroup(groupId);
        assertAdmin(group, requesterEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
            throw new IllegalStateException("User is already a member");
        }
        addMember(group, user, GroupMember.Role.MEMBER);
    }

    @Transactional
    public void removeMember(Long groupId, Long userId, String requesterEmail) {
        Group group = getGroup(groupId);
        assertAdmin(group, requesterEmail);
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    @Transactional
    public void deleteGroup(Long groupId, String requesterEmail) {
        Group group = getGroup(groupId);
        assertAdmin(group, requesterEmail);
        group.setActive(false);
        groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<Group> getUserGroups(String email) {
        User user = findUser(email);
        return groupRepository.findGroupsByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public Group getGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    @Transactional(readOnly = true)
    public List<GroupMember> getMembers(Long groupId) {
        return groupMemberRepository.findByGroupIdWithUser(groupId);
    }

    private void addMember(Group group, User user, GroupMember.Role role) {
        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(role)
                .build();
        groupMemberRepository.save(member);
    }

    private void assertAdmin(Group group, String email) {
        User requester = findUser(email);
        groupMemberRepository.findByGroupIdAndUserId(group.getId(), requester.getId())
                .filter(m -> m.getRole() == GroupMember.Role.ADMIN)
                .orElseThrow(() -> new SecurityException("Only group admins can perform this action"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }
}
