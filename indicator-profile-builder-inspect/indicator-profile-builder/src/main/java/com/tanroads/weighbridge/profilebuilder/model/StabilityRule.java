package com.tanroads.weighbridge.profilebuilder.model;

/** NONE: accept immediately (what the legacy system does). STATUS_TOKEN: trust the
 *  indicator's own stable marker. CONSECUTIVE_MATCH: N readings within tolerance.
 *  DURATION: readings stay within tolerance for a time window. */
public enum StabilityRule {
    NONE,
    STATUS_TOKEN,
    CONSECUTIVE_MATCH,
    DURATION
}
