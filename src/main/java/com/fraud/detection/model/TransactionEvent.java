package com.fraud.detection.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEvent(
        String transactionId,
        String userId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategory,
        double latitude,
        double longitude,
        Instant timestamp
) {
}
