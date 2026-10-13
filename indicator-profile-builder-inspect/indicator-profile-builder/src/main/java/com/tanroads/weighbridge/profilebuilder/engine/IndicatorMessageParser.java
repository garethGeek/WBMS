package com.tanroads.weighbridge.profilebuilder.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;

/**
 * Turns one complete, framed raw message into a ParsedMessage per the ExtractionRule.
 * Pure and stateless. Port of the main system class: malformed input is always rejected
 * with a reason, never coerced into a value.
 */
public class IndicatorMessageParser {

    private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)([a-zA-Z]*)");

    public ParsedMessage parse(String rawMessage, IndicatorProfile profile) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return ParsedMessage.rejected("empty message", rawMessage);
        }
        if (profile.getExtractionRule() == null) {
            return ParsedMessage.rejected("no extraction rule configured", rawMessage);
        }
        String trimmed = rawMessage.trim();
        return switch (profile.getExtractionRule()) {
            case FIRST_NUMBER -> parseFirstNumber(trimmed, profile);
            case DELIMITED_TOKENS -> parseDelimitedTokens(trimmed, profile);
            case FIXED_POSITION -> parseFixedPosition(trimmed, profile);
            case REGEX -> parseRegex(trimmed, profile);
            case STATUS_TOKEN -> parseStatusToken(trimmed, profile);
        };
    }

    private ParsedMessage parseFirstNumber(String message, IndicatorProfile profile) {
        Matcher matcher = FIRST_NUMBER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return ParsedMessage.rejected("no numeric value found", message);
        }
        return toValueOrRejection(matcher.group(1), matcher.group(2), profile, message);
    }

    private ParsedMessage parseDelimitedTokens(String message, IndicatorProfile profile) {
        String separator = profile.getExtractionPattern();
        if (separator == null || separator.isEmpty()) {
            return ParsedMessage.rejected("no separator configured", message);
        }
        String[] tokens = message.split(Pattern.quote(separator));
        List<BigDecimal> values = new ArrayList<>();
        for (String token : tokens) {
            String candidate = token.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            Matcher matcher = FIRST_NUMBER_PATTERN.matcher(candidate);
            if (!matcher.matches()) {
                return ParsedMessage.rejected("malformed token: " + candidate, message);
            }
            ParsedMessage single = toValueOrRejection(matcher.group(1), matcher.group(2), profile, message);
            if (!single.isValid()) {
                return single;
            }
            values.add(single.getFirstValue());
        }
        if (values.isEmpty()) {
            return ParsedMessage.rejected("no numeric tokens found", message);
        }
        return ParsedMessage.accepted(values, null, message);
    }

    private ParsedMessage parseFixedPosition(String message, IndicatorProfile profile) {
        String spec = profile.getExtractionPattern();
        if (spec == null || !spec.contains(",")) {
            return ParsedMessage.rejected("fixed-position spec must be offset,length", message);
        }
        String[] parts = spec.split(",");
        int offset;
        int length;
        try {
            offset = Integer.parseInt(parts[0].trim());
            length = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return ParsedMessage.rejected("invalid fixed-position spec", message);
        }
        if (offset < 0 || length <= 0 || offset + length > message.length()) {
            return ParsedMessage.rejected("message too short for configured fixed position", message);
        }
        String extracted = message.substring(offset, offset + length).trim();
        Matcher matcher = FIRST_NUMBER_PATTERN.matcher(extracted);
        if (!matcher.matches()) {
            return ParsedMessage.rejected("malformed value at fixed position: " + extracted, message);
        }
        return toValueOrRejection(matcher.group(1), matcher.group(2), profile, message);
    }

    private ParsedMessage parseRegex(String message, IndicatorProfile profile) {
        String pattern = profile.getExtractionPattern();
        if (pattern == null || pattern.isEmpty()) {
            return ParsedMessage.rejected("no regex configured", message);
        }
        Matcher matcher;
        try {
            matcher = Pattern.compile(pattern).matcher(message);
        } catch (Exception e) {
            return ParsedMessage.rejected("invalid configured regex", message);
        }
        if (!matcher.find() || matcher.groupCount() < 1) {
            return ParsedMessage.rejected("configured regex did not match", message);
        }
        return toValueOrRejection(matcher.group(1), "", profile, message);
    }

    private ParsedMessage parseStatusToken(String message, IndicatorProfile profile) {
        String pattern = profile.getExtractionPattern();
        if (pattern == null || pattern.isEmpty()) {
            return ParsedMessage.rejected("no status-token regex configured", message);
        }
        Matcher matcher;
        try {
            matcher = Pattern.compile(pattern).matcher(message);
        } catch (Exception e) {
            return ParsedMessage.rejected("invalid configured regex", message);
        }
        if (!matcher.find() || matcher.groupCount() < 2) {
            return ParsedMessage.rejected("status-token regex requires two groups: status, value", message);
        }
        String status = matcher.group(1);
        if (status == null || !status.matches("[A-Za-z]+")) {
            return ParsedMessage.rejected("invalid status token", message);
        }
        ParsedMessage valueResult = toValueOrRejection(matcher.group(2), "", profile, message);
        if (!valueResult.isValid()) {
            return valueResult;
        }
        return ParsedMessage.accepted(valueResult.getValues(), status, message);
    }

    private ParsedMessage toValueOrRejection(String numericPart, String unitSuffix, IndicatorProfile profile, String rawMessage) {
        BigDecimal value;
        try {
            value = new BigDecimal(numericPart);
        } catch (NumberFormatException e) {
            return ParsedMessage.rejected("malformed numeric value: " + numericPart, rawMessage);
        }
        if (value.signum() < 0) {
            return ParsedMessage.rejected("impossible negative weight value", rawMessage);
        }
        String expectedUnit = profile.getWeightUnit();
        if (unitSuffix != null && !unitSuffix.isEmpty() && expectedUnit != null && !expectedUnit.isEmpty()
                && !unitSuffix.equalsIgnoreCase(expectedUnit)) {
            return ParsedMessage.rejected("unexpected unit: " + unitSuffix, rawMessage);
        }
        List<BigDecimal> values = new ArrayList<>();
        values.add(value);
        return ParsedMessage.accepted(values, null, rawMessage);
    }
}
