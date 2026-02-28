package com.expensetracker.repository;

import com.expensetracker.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.user.id = :userId AND g.active = true ORDER BY g.createdAt DESC")
    List<Group> findGroupsByUserId(@Param("userId") Long userId);

    @Query("SELECT g FROM Group g WHERE g.createdBy.id = :userId AND g.active = true")
    List<Group> findByCreatedById(@Param("userId") Long userId);
}
