package com.expensetracker.service;
import com.expensetracker.model.Group;
import com.expensetracker.model.GroupMember;
import com.expensetracker.model.User;
import com.expensetracker.repository.GroupMemberRepository;
import com.expensetracker.repository.GroupRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public GroupMember addMember(Long groupId, Long userId) {

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalStateException("User is already a member.");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Group not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMember.Role.MEMBER)
                .build();

        return groupMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long groupId, Long userId) {

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException("User is not a member of this group.");
        }

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Transactional
    public String addOrInviteMember(Long groupId, String email, Long invitedByUserId,
                                    GroupInviteService inviteService) {

        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            if (groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
                throw new IllegalStateException("User is already a member.");
            }

            Group group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Group not found"));

            GroupMember member = GroupMember.builder()
                    .group(group)
                    .user(user)
                    .role(GroupMember.Role.MEMBER)
                    .build();
            groupMemberRepository.save(member);

            return "added";
        } else {
            inviteService.inviteUserToGroup(groupId, email, invitedByUserId);
            return "invited";
        }
    }
}
