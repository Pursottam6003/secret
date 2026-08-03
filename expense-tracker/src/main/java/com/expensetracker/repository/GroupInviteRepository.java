package com.expensetracker.repository;

import com.expensetracker.model.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    Optional<GroupInvite> findByToken(String token);

    boolean existsByGroupIdAndEmail(Long groupId, String email);
}