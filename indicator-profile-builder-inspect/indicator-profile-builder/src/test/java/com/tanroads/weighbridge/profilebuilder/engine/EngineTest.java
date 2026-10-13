package com.tanroads.weighbridge.profilebuilder.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tanroads.weighbridge.profilebuilder.model.ExtractionRule;
import com.tanroads.weighbridge.profilebuilder.model.FramingMode;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ReadingStability;
import com.tanroads.weighbridge.profilebuilder.model.StabilityRule;

class EngineTest {

    private static final String CRLF = "" + (char) 13 + (char) 10;

    private static IndicatorProfile profile(ExtractionRule rule, String pattern) {
        return new IndicatorProfile().setFramingMode(FramingMode.DELIMITER).setMessageDelimiter(CRLF)
                .setExtractionRule(rule).setExtractionPattern(pattern).setWeightUnit("kg")
                .setStabilityRule(StabilityRule.NONE);
    }

    @Test
    void packetizerReassemblesFragmentsAndSplitsMultipleMessages() {
        MessagePacketizer packetizer = new MessagePacketizer(profile(ExtractionRule.FIRST_NUMBER, null));
        assertTrue(packetizer.accept("ST,GS, 123".getBytes(StandardCharsets.US_ASCII)).messages().isEmpty());
        assertEquals(1, packetizer.pendingBytes() > 0 ? 1 : 0);
        List<String> messages = packetizer.accept(("40 kg" + CRLF + "US,GS, 12350 kg" + CRLF + "ST").getBytes(StandardCharsets.US_ASCII)).messages();
        assertEquals(List.of("ST,GS, 12340 kg", "US,GS, 12350 kg"), messages);
        assertEquals(2, packetizer.pendingBytes());
    }

    @Test
    void packetizerFixedLength() {
        IndicatorProfile p = profile(ExtractionRule.FIRST_NUMBER, null).setFramingMode(FramingMode.FIXED_LENGTH).setFixedMessageLength(4);
        MessagePacketizer packetizer = new MessagePacketizer(p);
        assertEquals(List.of("1234", "5678"), packetizer.accept("123456789".getBytes(StandardCharsets.US_ASCII)).messages());
        assertEquals(1, packetizer.pendingBytes());
    }

    @Test
    void packetizerOverflowWhenDelimiterNeverArrives() {
        IndicatorProfile p = profile(ExtractionRule.FIRST_NUMBER, null).setMaxBufferBytes(8);
        MessagePacketizer packetizer = new MessagePacketizer(p);
        assertEquals(MessagePacketizer.Outcome.BUFFER_OVERFLOW, packetizer.accept("0123456789".getBytes(StandardCharsets.US_ASCII)).outcome());
        assertEquals(0, packetizer.pendingBytes());
    }

    @Test
    void parserFirstNumberWithUnitSuffix() {
        IndicatorMessageParser parser = new IndicatorMessageParser();
        ParsedMessage parsed = parser.parse("ST,GS,  12340kg", profile(ExtractionRule.FIRST_NUMBER, null));
        assertTrue(parsed.isValid());
        assertEquals(new BigDecimal("12340"), parsed.getFirstValue());
        ParsedMessage wrongUnit = parser.parse("12340lb", profile(ExtractionRule.FIRST_NUMBER, null));
        assertFalse(wrongUnit.isValid());
        assertEquals("unexpected unit: lb", wrongUnit.getRejectionReason());
        assertFalse(parser.parse("-5 kg", profile(ExtractionRule.FIRST_NUMBER, null)).isValid());
        assertFalse(parser.parse("no digits", profile(ExtractionRule.FIRST_NUMBER, null)).isValid());
    }

    @Test
    void parserDelimitedTokensMultiDeck() {
        ParsedMessage parsed = new IndicatorMessageParser().parse("1200kg2300kg3400kg", profile(ExtractionRule.DELIMITED_TOKENS, "kg"));
        assertTrue(parsed.isValid());
        assertEquals(3, parsed.getValues().size());
        assertEquals(new BigDecimal("3400"), parsed.getValues().get(2));
    }

    @Test
    void parserFixedPositionRegexAndStatusToken() {
        IndicatorMessageParser parser = new IndicatorMessageParser();
        assertEquals(new BigDecimal("12340"), parser.parse("ST,GS,  12340 kg", profile(ExtractionRule.FIXED_POSITION, "6,7")).getFirstValue());
        assertEquals(new BigDecimal("12340"), parser.parse("ST,GS,  12340 kg", profile(ExtractionRule.REGEX, "GS,\\s*(\\d+)")).getFirstValue());
        ParsedMessage status = parser.parse("US,GS,  12340 kg", profile(ExtractionRule.STATUS_TOKEN, "^(ST|US),GS,\\s*(\\d+)"));
        assertTrue(status.isValid());
        assertEquals("US", status.getStatusToken());
        assertFalse(parser.parse("ST,GS, 12340", profile(ExtractionRule.REGEX, "(")).isValid());
    }

    @Test
    void stabilityStatusTokenAndConsecutiveMatch() {
        IndicatorProfile tokenProfile = profile(ExtractionRule.STATUS_TOKEN, "^(ST|US),GS,\\s*(\\d+)")
                .setStabilityRule(StabilityRule.STATUS_TOKEN).setStableStatusToken("ST");
        ParsePipeline pipeline = new ParsePipeline(tokenProfile, "test");
        assertEquals(ReadingStability.UNSTABLE, pipeline.parseOne("US,GS, 100").reading().getStability());
        assertEquals(ReadingStability.STABLE, pipeline.parseOne("ST,GS, 100").reading().getStability());

        IndicatorProfile consecutive = profile(ExtractionRule.FIRST_NUMBER, null)
                .setStabilityRule(StabilityRule.CONSECUTIVE_MATCH).setRequiredConsecutiveMatches(3).setStabilityTolerance(new BigDecimal("10"));
        ParsePipeline p2 = new ParsePipeline(consecutive, "test");
        assertEquals(ReadingStability.UNSTABLE, p2.parseOne("100").reading().getStability());
        assertEquals(ReadingStability.UNSTABLE, p2.parseOne("105").reading().getStability());
        assertEquals(ReadingStability.STABLE, p2.parseOne("108").reading().getStability());
        assertEquals(ReadingStability.UNSTABLE, p2.parseOne("200").reading().getStability());
    }

    @Test
    void pipelineFeedsChunksAndInvalidMessagesCarryReason() {
        ParsePipeline pipeline = new ParsePipeline(profile(ExtractionRule.FIRST_NUMBER, null), "test");
        List<ParsePipeline.Event> events = pipeline.feed(("ST,GS, 12340 kg" + CRLF + "garbage" + CRLF).getBytes(StandardCharsets.US_ASCII));
        assertEquals(2, events.size());
        assertEquals(new BigDecimal("12340"), events.get(0).reading().getWeight());
        assertNull(events.get(1).reading().getWeight());
        assertEquals(ReadingStability.INVALID, events.get(1).reading().getStability());
        assertEquals("no numeric value found", events.get(1).reading().getRejectionReason());
        CaptureStatistics stats = new CaptureStatistics();
        events.forEach(e -> stats.record(e.reading()));
        assertEquals(2, stats.getMessagesReceived());
        assertEquals(1, stats.getValidCount());
        assertEquals(1, stats.getInvalidCount());
    }
}
