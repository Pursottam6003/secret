package com.expensetracker.repository;

import com.expensetracker.model.Settlement;
import com.expensetracker.model.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<Settlement> findByGroupIdAndStatus(Long groupId, SettlementStatus status);

    @Query("SELECT s FROM Settlement s WHERE s.group.id = :groupId AND (s.payer.id = :userId OR s.receiver.id = :userId) ORDER BY s.createdAt DESC")
    List<Settlement> findByGroupIdAndUserId(@Param("groupId") Long groupId,
                                            @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.payer.id = :payerId AND s.receiver.id = :receiverId AND s.status = 'COMPLETED'")
    BigDecimal sumCompletedSettlements(@Param("groupId") Long groupId,
                                       @Param("payerId") Long payerId,
                                       @Param("receiverId") Long receiverId);
}
