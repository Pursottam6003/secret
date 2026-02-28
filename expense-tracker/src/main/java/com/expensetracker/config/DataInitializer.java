package com.expensetracker.config;

import com.expensetracker.model.Group;
import com.expensetracker.model.GroupMember;
import com.expensetracker.model.User;
import com.expensetracker.repository.GroupMemberRepository;
import com.expensetracker.repository.GroupRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds demo data when running with the 'dev' profile.
 * Creates 3 demo users + 1 group so you can immediately test the UI.
 */
@Slf4j
@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository        userRepository;
    private final GroupRepository       groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PasswordEncoder       passwordEncoder;

    @Bean
    CommandLineRunner seedDemoData() {
        return args -> {
            if (userRepository.count() > 0) return; // already seeded

            log.info("Seeding demo data...");

            User alice = userRepository.save(User.builder()
                    .name("Alice Johnson")
                    .email("alice@demo.com")
                    .password(passwordEncoder.encode("password"))
                    .preferredCurrency("USD")
                    .build());

            User bob = userRepository.save(User.builder()
                    .name("Bob Smith")
                    .email("bob@demo.com")
                    .password(passwordEncoder.encode("password"))
                    .preferredCurrency("USD")
                    .build());

            User carol = userRepository.save(User.builder()
                    .name("Carol White")
                    .email("carol@demo.com")
                    .password(passwordEncoder.encode("password"))
                    .preferredCurrency("USD")
                    .build());

            Group group = groupRepository.save(Group.builder()
                    .name("Weekend Trip")
                    .description("Vegas road trip expenses")
                    .defaultCurrency("USD")
                    .createdBy(alice)
                    .build());

            groupMemberRepository.save(GroupMember.builder().group(group).user(alice).role(GroupMember.Role.ADMIN).build());
            groupMemberRepository.save(GroupMember.builder().group(group).user(bob).role(GroupMember.Role.MEMBER).build());
            groupMemberRepository.save(GroupMember.builder().group(group).user(carol).role(GroupMember.Role.MEMBER).build());

            log.info("Demo data seeded. Login: alice@demo.com / password");
        };
    }
}
