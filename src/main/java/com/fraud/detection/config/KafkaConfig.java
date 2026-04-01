package com.fraud.detection.config;

/**
 * Canonical Kafka topic names for the fraud engine. Use these constants everywhere topics are referenced in Java.
 */
public final class KafkaConfig {

    public static final String TOPIC_TRANSACTIONS_INBOUND = "transactions-inbound";
    public static final String TOPIC_FRAUD_ALERTS = "fraud-alerts";

    private KafkaConfig() {
    }
}
