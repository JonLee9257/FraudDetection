package com.fraud.detection.model;

import java.time.Instant;

/**
 * Emitted when velocity rules fire (e.g. too many transactions per user in a time window).
 */
public record FraudAlert(
        String userId,
        String alertType,
        String message,
        String triggeringTransactionId,
        int transactionCountInWindow,
        Instant detectedAt
) {
}
