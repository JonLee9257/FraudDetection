package com.fraud.detection.producer;

import com.fraud.detection.config.KafkaConfig;
import com.fraud.detection.model.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class TransactionKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaProducer.class);

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public TransactionKafkaProducer(KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a transaction event to the inbound topic (simulated high-throughput producer path).
     */
    public CompletableFuture<SendResult<String, TransactionEvent>> send(TransactionEvent event) {
        return kafkaTemplate
                .send(KafkaConfig.TOPIC_TRANSACTIONS_INBOUND, event.transactionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send transaction {} to {}: {}", event.transactionId(),
                                KafkaConfig.TOPIC_TRANSACTIONS_INBOUND, ex.getMessage());
                    } else {
                        log.debug("Sent transaction {} to partition {} offset {}",
                                event.transactionId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
