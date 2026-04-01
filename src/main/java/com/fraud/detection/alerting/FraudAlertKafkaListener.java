package com.fraud.detection.alerting;

import com.fraud.detection.config.KafkaConfig;
import com.fraud.detection.model.Disposition;
import com.fraud.detection.model.FraudAlert;
import com.fraud.detection.model.FraudDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code fraud-alerts}, applies a mocked fraud score, assigns a disposition (Eno-style),
 * and writes an audit-friendly {@link FraudDecision} to the application log.
 */
@Component
public class FraudAlertKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertKafkaListener.class);

    private final MockFraudScoreService mockFraudScoreService;

    public FraudAlertKafkaListener(MockFraudScoreService mockFraudScoreService) {
        this.mockFraudScoreService = mockFraudScoreService;
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_FRAUD_ALERTS,
            containerFactory = "fraudAlertKafkaListenerContainerFactory")
    public void onFraudAlert(FraudAlert alert) {
        double score = mockFraudScoreService.mockScore(alert);
        Disposition disposition = resolveDisposition(score);
        boolean mfaTriggered = false;

        if (disposition == Disposition.CHALLENGE) {
            mfaTriggered = true;
            simulateMfaChallenge(alert);
        }

        FraudDecision decision = FraudDecision.fromAlert(alert, score, disposition, mfaTriggered);

        log.info(
                "Fraud alert processed | Disposition={} | fraudScore={} | userId={} | alertType={} | txId={} | "
                        + "transactionCountInWindow={} | mfaChallengeTriggered={} | auditDecision={}",
                disposition,
                String.format("%.4f", score),
                alert.userId(),
                alert.alertType(),
                alert.triggeringTransactionId(),
                alert.transactionCountInWindow(),
                mfaTriggered,
                decision);
    }

    static Disposition resolveDisposition(double fraudScore) {
        if (fraudScore > 0.8d) {
            return Disposition.BLOCK;
        }
        if (fraudScore >= 0.5d) {
            return Disposition.CHALLENGE;
        }
        return Disposition.ALLOW;
    }

    private void simulateMfaChallenge(FraudAlert alert) {
        log.warn(
                "MFA trigger (simulated) | userId={} | transactionId={} | message=Issuing step-up challenge for velocity alert",
                alert.userId(),
                alert.triggeringTransactionId());
    }
}
