package com.tanroads.weighbridge.profilebuilder.engine;

import java.util.ArrayList;
import java.util.List;

import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;

/**
 * Packetizer + parser + stability engine for one set of parsing rules. The live capture
 * session holds one of these and REPLACES it whenever the technician clicks "apply rules"
 * in the UI, so extraction/framing/stability can be tuned against real, live raw data
 * without dropping the hardware connection. A replayed (offline) run over previously
 * captured chunks uses a fresh throwaway instance.
 */
public final class ParsePipeline {

    public record Event(String rawMessage, NormalizedWeightReading reading, boolean bufferOverflow) { }

    private final IndicatorProfile rules;
    private final MessagePacketizer packetizer;
    private final IndicatorMessageParser parser = new IndicatorMessageParser();
    private final StabilityEngine stabilityEngine;
    private final String source;

    public ParsePipeline(IndicatorProfile rules, String source) {
        this.rules = rules;
        this.source = source;
        this.packetizer = new MessagePacketizer(rules);
        this.stabilityEngine = new StabilityEngine(rules);
    }

    public IndicatorProfile rules() {
        return rules;
    }

    /** Feeds one raw chunk; returns one event per complete message it produced (possibly
     *  none), or a single overflow event if the accumulation buffer was exceeded. */
    public synchronized List<Event> feed(byte[] chunk) {
        List<Event> events = new ArrayList<>();
        MessagePacketizer.Result result = packetizer.accept(chunk);
        if (result.outcome() == MessagePacketizer.Outcome.BUFFER_OVERFLOW) {
            events.add(new Event(null, NormalizedWeightReading.communicationError(source, null), true));
            return events;
        }
        for (String message : result.messages()) {
            ParsedMessage parsed = parser.parse(message, rules);
            events.add(new Event(message, stabilityEngine.evaluate(parsed, source), false));
        }
        return events;
    }

    /** Parses one already-framed message directly (no packetizer), for single-message tests. */
    public synchronized Event parseOne(String message) {
        ParsedMessage parsed = parser.parse(message, rules);
        return new Event(message, stabilityEngine.evaluate(parsed, source), false);
    }

    public synchronized int pendingBytes() {
        return packetizer.pendingBytes();
    }

    public synchronized void reset() {
        packetizer.reset();
        stabilityEngine.reset();
    }
}
