package com.tanroads.weighbridge.profilebuilder.engine;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** Result of IndicatorMessageParser on one complete, framed message. Port of the main
 *  system class of the same name. */
public final class ParsedMessage {
    private final boolean valid;
    private final List<BigDecimal> values;
    private final String statusToken;
    private final String rejectionReason;
    private final String rawMessage;

    private ParsedMessage(boolean valid, List<BigDecimal> values, String statusToken,
                          String rejectionReason, String rawMessage) {
        this.valid = valid;
        this.values = values;
        this.statusToken = statusToken;
        this.rejectionReason = rejectionReason;
        this.rawMessage = rawMessage;
    }

    public static ParsedMessage accepted(List<BigDecimal> values, String statusToken, String rawMessage) {
        return new ParsedMessage(true, Collections.unmodifiableList(values), statusToken, null, rawMessage);
    }

    public static ParsedMessage rejected(String reason, String rawMessage) {
        return new ParsedMessage(false, Collections.emptyList(), null, reason, rawMessage);
    }

    public boolean isValid() { return valid; }
    public List<BigDecimal> getValues() { return values; }
    public BigDecimal getFirstValue() { return values.isEmpty() ? null : values.get(0); }
    public String getStatusToken() { return statusToken; }
    public String getRejectionReason() { return rejectionReason; }
    public String getRawMessage() { return rawMessage; }
}
