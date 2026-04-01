package com.fraud.detection.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaInfrastructureConfig {

    @Bean
    public NewTopic transactionsInboundTopic() {
        // Creating with 3 partitions allows Flink to scale better
        return TopicBuilder.name(KafkaConfig.TOPIC_TRANSACTIONS_INBOUND)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
