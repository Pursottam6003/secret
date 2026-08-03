package com.expensetracker.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "group_invites",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "email"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false, unique = true)
    private String token; // UUID for secure invite link

    @Column(nullable = false)
    private String email;

    @ManyToOne
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime acceptedAt;

    @Column(name = "is_accepted")
    @Builder.Default
    private boolean accepted = false;
}