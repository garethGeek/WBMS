package com.tanroads.weighbridge.profilebuilder.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ReadingStability;
import com.tanroads.weighbridge.profilebuilder.model.StabilityRule;

/**
 * Judges whether the most recently parsed reading is STABLE per the configured
 * StabilityRule. Port of the main system class: mechanism only, no numeric default is
 * asserted as statutorily correct. NONE = accept immediately (what the legacy system does).
 * Multi-value (multi-deck) messages are judged on their first value only, as in production.
 */
public class StabilityEngine {

    private final IndicatorProfile profile;
    private final Deque<BigDecimal> recentValues = new ArrayDeque<>();
    private BigDecimal windowReferenceValue;
    private Instant windowStartedAt;

    public StabilityEngine(IndicatorProfile profile) {
        this.profile = profile;
    }

    public NormalizedWeightReading evaluate(ParsedMessage parsedMessage, String source) {
        if (parsedMessage == null || !parsedMessage.isValid()) {
            String raw = parsedMessage != null ? parsedMessage.getRawMessage() : null;
            String reason = parsedMessage != null ? parsedMessage.getRejectionReason() : "no message";
            return NormalizedWeightReading.invalid(source, raw, reason);
        }
        BigDecimal value = parsedMessage.getFirstValue();
        if (value == null) {
            return NormalizedWeightReading.invalid(source, parsedMessage.getRawMessage(), "no value");
        }
        StabilityRule rule = profile.getStabilityRule() != null ? profile.getStabilityRule() : StabilityRule.NONE;
        ReadingStability stability = switch (rule) {
            case NONE -> ReadingStability.STABLE;
            case STATUS_TOKEN -> evaluateStatusToken(parsedMessage);
            case CONSECUTIVE_MATCH -> evaluateConsecutiveMatch(value);
            case DURATION -> evaluateDuration(value);
        };
        return new NormalizedWeightReading(value, parsedMessage.getValues(), profile.getWeightUnit(), stability,
                Instant.now(), source, parsedMessage.getRawMessage(), parsedMessage.getStatusToken(), null);
    }

    public void reset() {
        recentValues.clear();
        windowReferenceValue = null;
        windowStartedAt = null;
    }

    private ReadingStability evaluateStatusToken(ParsedMessage parsedMessage) {
        String expected = profile.getStableStatusToken();
        String actual = parsedMessage.getStatusToken();
        if (expected == null || actual == null) {
            return ReadingStability.UNSTABLE;
        }
        return expected.trim().equalsIgnoreCase(actual.trim()) ? ReadingStability.STABLE : ReadingStability.UNSTABLE;
    }

    private ReadingStability evaluateConsecutiveMatch(BigDecimal value) {
        int required = profile.getRequiredConsecutiveMatches() != null ? profile.getRequiredConsecutiveMatches() : 1;
        BigDecimal tolerance = profile.getStabilityTolerance() != null ? profile.getStabilityTolerance() : BigDecimal.ZERO;
        recentValues.addLast(value);
        while (recentValues.size() > required) {
            recentValues.removeFirst();
        }
        if (recentValues.size() < required) {
            return ReadingStability.UNSTABLE;
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (BigDecimal candidate : recentValues) {
            if (min == null || candidate.compareTo(min) < 0) {
                min = candidate;
            }
            if (max == null || candidate.compareTo(max) > 0) {
                max = candidate;
            }
        }
        return max.subtract(min).compareTo(tolerance) <= 0 ? ReadingStability.STABLE : ReadingStability.UNSTABLE;
    }

    private ReadingStability evaluateDuration(BigDecimal value) {
        BigDecimal tolerance = profile.getStabilityTolerance() != null ? profile.getStabilityTolerance() : BigDecimal.ZERO;
        long requiredMs = profile.getStabilityDurationMs() != null ? profile.getStabilityDurationMs() : 0;
        if (windowReferenceValue == null || value.subtract(windowReferenceValue).abs().compareTo(tolerance) > 0) {
            windowReferenceValue = value;
            windowStartedAt = Instant.now();
            return ReadingStability.UNSTABLE;
        }
        long elapsedMs = Instant.now().toEpochMilli() - windowStartedAt.toEpochMilli();
        return elapsedMs >= requiredMs ? ReadingStability.STABLE : ReadingStability.UNSTABLE;
    }
}
