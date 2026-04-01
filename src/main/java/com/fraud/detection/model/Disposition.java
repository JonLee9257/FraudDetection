package com.fraud.detection.model;

/**
 * Decision outcome for a fraud alert (audit / operations).
 */
public enum Disposition {
    /** Decline or hard-stop the activity. */
    BLOCK,
    /** Step-up verification (e.g. MFA). */
    CHALLENGE,
    /** No blocking action from this path (low modeled risk). */
    ALLOW
}
