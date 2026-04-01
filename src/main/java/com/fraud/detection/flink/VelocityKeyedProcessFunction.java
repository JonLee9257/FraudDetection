package com.fraud.detection.flink;

import com.fraud.detection.model.FraudAlert;
import com.fraud.detection.model.TransactionEvent;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;

/**
 * Event-time velocity check per user: more than {@code maxTransactionsPerWindow} transactions
 * within {@code windowMs} triggers an alert. Uses {@link ValueState} and an event-time timer to reset.
 */
public class VelocityKeyedProcessFunction extends KeyedProcessFunction<String, TransactionEvent, FraudAlert> {

    private final long windowMs;
    private final int maxTransactionsPerWindow;

    private transient ValueState<Integer> transactionCount;
    private transient ValueState<Long> windowStartTime;
    private transient ValueState<Boolean> alertAlreadySent;

    public VelocityKeyedProcessFunction(long windowMs, int maxTransactionsPerWindow) {
        this.windowMs = windowMs;
        this.maxTransactionsPerWindow = maxTransactionsPerWindow;
    }

    @Override
    public void open(Configuration parameters) {
        transactionCount = getRuntimeContext().getState(
                new ValueStateDescriptor<>("transactionCount", Types.INT));
        windowStartTime = getRuntimeContext().getState(
                new ValueStateDescriptor<>("windowStartTime", Types.LONG));
        alertAlreadySent = getRuntimeContext().getState(
                new ValueStateDescriptor<>("alertAlreadySent", Types.BOOLEAN));
    }

    @Override
    public void processElement(TransactionEvent value, Context ctx, Collector<FraudAlert> out) throws Exception {
        long eventTime = eventTimestampMillis(value, ctx.timestamp());
        Long start = windowStartTime.value();

        if (start == null) {
            windowStartTime.update(eventTime);
            transactionCount.update(1);
            ctx.timerService().registerEventTimeTimer(eventTime + windowMs);
            return;
        }

        int prev = transactionCount.value() == null ? 0 : transactionCount.value();
        int count = prev + 1;
        transactionCount.update(count);

        if (count > maxTransactionsPerWindow && !Boolean.TRUE.equals(alertAlreadySent.value())) {
            alertAlreadySent.update(true);
            Instant detectedAt = value.timestamp() != null ? value.timestamp() : Instant.ofEpochMilli(eventTime);
            out.collect(new FraudAlert(
                    value.userId(),
                    "VELOCITY",
                    "User exceeded %d transactions in a rolling %d-second window"
                            .formatted(maxTransactionsPerWindow, windowMs / 1000),
                    value.transactionId(),
                    count,
                    detectedAt));
        }
    }

    private static long eventTimestampMillis(TransactionEvent value, long ctxTimestamp) {
        if (value.timestamp() != null) {
            return value.timestamp().toEpochMilli();
        }
        return ctxTimestamp > 0 ? ctxTimestamp : System.currentTimeMillis();
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<FraudAlert> out) {
        transactionCount.clear();
        windowStartTime.clear();
        alertAlreadySent.clear();
    }
}
