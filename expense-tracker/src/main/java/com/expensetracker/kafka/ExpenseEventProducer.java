package com.expensetracker.kafka;

import com.expensetracker.config.KafkaConfig;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("prod")
public class ExpenseEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendExpenseCreated(ExpenseEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_EXPENSE_CREATED, event.getGroupId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send expense.created event: {}", ex.getMessage());
                    } else {
                        log.debug("Sent expense.created for group {}", event.getGroupId());
                    }
                });
    }

    public void sendSettlementCompleted(SettlementEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_SETTLEMENT_DONE, event.getGroupId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send settlement.completed event: {}", ex.getMessage());
                    }
                });
    }

    public void sendGroupActivity(GroupActivityEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_GROUP_ACTIVITY, event.getGroupId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send group.activity event: {}", ex.getMessage());
                    }
                });
    }

    @Data @Builder
    public static class ExpenseEvent {
        private Long        expenseId;
        private Long        groupId;
        private String      groupName;
        private String      description;
        private BigDecimal  amount;
        private String      currency;
        private String      paidByName;
        private LocalDateTime timestamp;
    }

    @Data @Builder
    public static class SettlementEvent {
        private Long       settlementId;
        private Long       groupId;
        private String     payerName;
        private String     receiverName;
        private BigDecimal amount;
        private String     currency;
        private LocalDateTime timestamp;
    }

    @Data @Builder
    public static class GroupActivityEvent {
        private Long      groupId;
        private String    actorEmail;
        private String    action;
        private String    detail;
        private LocalDateTime timestamp;
    }
}
