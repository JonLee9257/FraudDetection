package com.fraud.detection.model;

import java.time.Instant;

/**
 * Immutable audit record for a scored fraud alert decision (Eno-style disposition trail).
 */
public record FraudDecision(
        Instant decidedAt,
        String userId,
        String alertType,
        String triggeringTransactionId,
        int transactionCountInWindow,
        Instant alertDetectedAt,
        double fraudScore,
        Disposition disposition,
        boolean mfaChallengeTriggered
) {
    public static FraudDecision fromAlert(
            FraudAlert alert,
            double fraudScore,
            Disposition disposition,
            boolean mfaChallengeTriggered) {
        return new FraudDecision(
                Instant.now(),
                alert.userId(),
                alert.alertType(),
                alert.triggeringTransactionId(),
                alert.transactionCountInWindow(),
                alert.detectedAt(),
                fraudScore,
                disposition,
                mfaChallengeTriggered);
    }
}
