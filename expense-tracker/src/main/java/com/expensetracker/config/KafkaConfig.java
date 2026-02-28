package com.expensetracker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("prod")
public class KafkaConfig {

    public static final String TOPIC_EXPENSE_CREATED  = "expense.created";
    public static final String TOPIC_EXPENSE_SETTLED  = "expense.settled";
    public static final String TOPIC_SETTLEMENT_DONE  = "settlement.completed";
    public static final String TOPIC_GROUP_ACTIVITY   = "group.activity";
    public static final String TOPIC_NOTIFICATION     = "notification.push";

    @Bean
    public NewTopic expenseCreatedTopic() {
        return TopicBuilder.name(TOPIC_EXPENSE_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic expenseSettledTopic() {
        return TopicBuilder.name(TOPIC_EXPENSE_SETTLED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic settlementDoneTopic() {
        return TopicBuilder.name(TOPIC_SETTLEMENT_DONE).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic groupActivityTopic() {
        return TopicBuilder.name(TOPIC_GROUP_ACTIVITY).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name(TOPIC_NOTIFICATION).partitions(3).replicas(1).build();
    }
}
