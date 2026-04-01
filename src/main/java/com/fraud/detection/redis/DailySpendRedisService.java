package com.fraud.detection.redis;

import com.fraud.detection.config.FraudDetectionProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Hot feature store: per-user daily spend totals in Redis (namespaced keys {@code user:v1:daily_total:{userId}}),
 * with TTL to the end of the configured calendar day.
 */
@Service
public class DailySpendRedisService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final StringRedisTemplate stringRedisTemplate;
    private final FraudDetectionProperties fraudDetectionProperties;

    public DailySpendRedisService(
            StringRedisTemplate stringRedisTemplate,
            FraudDetectionProperties fraudDetectionProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.fraudDetectionProperties = fraudDetectionProperties;
    }

    /**
     * Redis key for the user's running daily total (string holding a decimal amount).
     */
    public String dailyTotalKey(String userId) {
        return "user:v1:daily_total:%s".formatted(Objects.requireNonNull(userId, "userId"));
    }

    /**
     * Adds {@code amount} to the user's daily total (atomic INCRBYFLOAT) and refreshes expiry to end of day.
     *
     * @return total daily spend after this transaction (2 decimal places)
     */
    public BigDecimal recordTransactionAndIncrementDailyTotal(String userId, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        String key = dailyTotalKey(userId);
        Double after = stringRedisTemplate.opsForValue().increment(key, amount.doubleValue());
        if (after == null) {
            throw new IllegalStateException("Redis INCRBYFLOAT returned null for key " + key);
        }
        expireAtEndOfCalendarDay(key);
        return BigDecimal.valueOf(after).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @return whether {@code currentDailyTotal + newTransactionAmount} would exceed the configured daily limit
     *         (default $5,000 from {@code fraud.redis.daily-spend-limit-usd}).
     */
    public boolean wouldExceedDailySpendLimit(String userId, BigDecimal newTransactionAmount) {
        BigDecimal limit = fraudDetectionProperties.redis().dailySpendLimitUsd();
        return wouldExceedDailySpendLimit(userId, newTransactionAmount, limit);
    }

    /**
     * @return whether {@code currentDailyTotal + newTransactionAmount} would exceed {@code limit}.
     */
    public boolean wouldExceedDailySpendLimit(
            String userId, BigDecimal newTransactionAmount, BigDecimal limit) {
        Objects.requireNonNull(newTransactionAmount, "newTransactionAmount");
        Objects.requireNonNull(limit, "limit");
        BigDecimal current = getCurrentDailyTotal(userId);
        BigDecimal projected = current.add(newTransactionAmount, MC).setScale(2, RoundingMode.HALF_UP);
        return projected.compareTo(limit) > 0;
    }

    /**
     * Current stored daily total for the user, or zero if missing.
     */
    public BigDecimal getCurrentDailyTotal(String userId) {
        String raw = stringRedisTemplate.opsForValue().get(dailyTotalKey(userId));
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
    }

    private void expireAtEndOfCalendarDay(String key) {
        long seconds = secondsUntilEndOfCalendarDay();
        if (seconds > 0) {
            stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
        }
    }

    private long secondsUntilEndOfCalendarDay() {
        ZoneId zone = ZoneId.of(fraudDetectionProperties.redis().zoneId());
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime startOfNextDay = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        return Math.max(1L, Duration.between(now, startOfNextDay).getSeconds());
    }
}
