package com.fraud.detection.alerting;

import com.fraud.detection.model.FraudAlert;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Deterministic mocked fraud score in {@code [0.0, 1.0)} for demos and tests (not production scoring).
 */
@Service
public class MockFraudScoreService {

    /**
     * Produces a stable pseudo-random score from alert fields so the same alert yields the same score.
     */
    public double mockScore(FraudAlert alert) {
        int h = Objects.hash(
                alert.userId(),
                alert.triggeringTransactionId(),
                alert.detectedAt(),
                alert.transactionCountInWindow());
        return (Math.abs(h) % 10_000) / 10_000.0;
    }
}
