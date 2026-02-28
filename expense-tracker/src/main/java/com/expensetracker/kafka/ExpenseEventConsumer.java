package com.expensetracker.kafka;

import com.expensetracker.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes Kafka events and broadcasts to connected WebSocket clients
 * so the UI updates in real-time without polling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("prod")
public class ExpenseEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = KafkaConfig.TOPIC_EXPENSE_CREATED, groupId = "expense-tracker-group")
    public void onExpenseCreated(ExpenseEventProducer.ExpenseEvent event) {
        log.info("Received expense.created: expenseId={}", event.getExpenseId());
        // Push real-time update to all group subscribers
        messagingTemplate.convertAndSend(
                "/topic/group/" + event.getGroupId() + "/expenses",
                event);
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_SETTLEMENT_DONE, groupId = "expense-tracker-group")
    public void onSettlementCompleted(ExpenseEventProducer.SettlementEvent event) {
        log.info("Received settlement.completed: settlementId={}", event.getSettlementId());
        messagingTemplate.convertAndSend(
                "/topic/group/" + event.getGroupId() + "/settlements",
                event);
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_GROUP_ACTIVITY, groupId = "expense-tracker-group")
    public void onGroupActivity(ExpenseEventProducer.GroupActivityEvent event) {
        log.info("Group activity: group={} action={}", event.getGroupId(), event.getAction());
        messagingTemplate.convertAndSend(
                "/topic/group/" + event.getGroupId() + "/activity",
                event);
    }
}
