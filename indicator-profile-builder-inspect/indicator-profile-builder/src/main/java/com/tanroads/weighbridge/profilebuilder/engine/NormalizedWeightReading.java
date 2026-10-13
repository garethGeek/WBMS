package com.tanroads.weighbridge.profilebuilder.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.tanroads.weighbridge.profilebuilder.model.ReadingStability;

/** Trusted output of the pipeline (transport -> packetizer -> parser -> stability).
 *  Port of the main system class, plus the full value list (multi-deck) and the parser
 *  rejection reason so the technician UI can explain WHY a message was rejected. */
public final class NormalizedWeightReading {
    private final BigDecimal weight;
    private final List<BigDecimal> values;
    private final String unit;
    private final ReadingStability stability;
    private final Instant timestamp;
    private final String source;
    private final String rawMessageReference;
    private final String statusToken;
    private final String rejectionReason;

    public NormalizedWeightReading(BigDecimal weight, List<BigDecimal> values, String unit, ReadingStability stability,
                                   Instant timestamp, String source, String rawMessageReference,
                                   String statusToken, String rejectionReason) {
        this.weight = weight;
        this.values = values != null ? List.copyOf(values) : List.of();
        this.unit = unit;
        this.stability = stability;
        this.timestamp = timestamp;
        this.source = source;
        this.rawMessageReference = rawMessageReference;
        this.statusToken = statusToken;
        this.rejectionReason = rejectionReason;
    }

    public static NormalizedWeightReading communicationError(String source, String rawMessageReference) {
        return new NormalizedWeightReading(null, null, null, ReadingStability.COMMUNICATION_ERROR, Instant.now(),
                source, rawMessageReference, null, "communication error");
    }

    public static NormalizedWeightReading invalid(String source, String rawMessageReference, String reason) {
        return new NormalizedWeightReading(null, null, null, ReadingStability.INVALID, Instant.now(),
                source, rawMessageReference, null, reason);
    }

    public BigDecimal getWeight() { return weight; }
    public List<BigDecimal> getValues() { return values; }
    public String getUnit() { return unit; }
    public ReadingStability getStability() { return stability; }
    public Instant getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public String getRawMessageReference() { return rawMessageReference; }
    public String getStatusToken() { return statusToken; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isUsable() { return stability == ReadingStability.STABLE && weight != null; }
}
