package com.fraud.detection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Fraud detection settings (Kafka consumer groups, velocity rules, Flink, Redis hot store). Topic names live in
 * {@link KafkaConfig}. Kafka bootstrap servers come from {@code spring.kafka.bootstrap-servers}.
 */
@ConfigurationProperties(prefix = "fraud")
public record FraudDetectionProperties(
        Kafka kafka,
        Velocity velocity,
        Flink flink,
        RedisHotStore redis
) {

    public record Kafka(
            @DefaultValue("fraud-velocity-flink") String consumerGroupId,
            /** Consumer group for the Spring Boot fraud-alerts listener (Eno-style alerting). */
            @DefaultValue("fraud-eno-alerting-service") String alertsListenerConsumerGroupId
    ) {
    }

    public record Velocity(
            @DefaultValue("60") long windowSeconds,
            @DefaultValue("3") int maxTransactionsPerWindow
    ) {
    }

    public record Flink(
            @DefaultValue("1") int parallelism
    ) {
    }

    /**
     * Redis-backed hot features (e.g. daily spend totals).
     */
    public record RedisHotStore(
            @DefaultValue("5000") BigDecimal dailySpendLimitUsd,
            /** IANA zone id used to define calendar-day boundaries for TTL (e.g. {@code America/New_York}). */
            @DefaultValue("UTC") String zoneId
    ) {
    }
}
