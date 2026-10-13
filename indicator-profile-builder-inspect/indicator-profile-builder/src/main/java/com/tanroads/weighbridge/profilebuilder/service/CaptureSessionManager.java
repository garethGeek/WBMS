package com.tanroads.weighbridge.profilebuilder.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.tanroads.weighbridge.profilebuilder.engine.CaptureStatistics;
import com.tanroads.weighbridge.profilebuilder.engine.NormalizedWeightReading;
import com.tanroads.weighbridge.profilebuilder.engine.ParsePipeline;
import com.tanroads.weighbridge.profilebuilder.model.ConnectionType;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;
import com.tanroads.weighbridge.profilebuilder.model.ReadMode;
import com.tanroads.weighbridge.profilebuilder.transport.IndicatorTransport;
import com.tanroads.weighbridge.profilebuilder.transport.SerialIndicatorTransport;
import com.tanroads.weighbridge.profilebuilder.transport.TcpClientIndicatorTransport;
import com.tanroads.weighbridge.profilebuilder.transport.TransportListener;

/**
 * The one live capture session of this (single technician, single laptop) tool. Owns the
 * transport, a hot-swappable ParsePipeline, the running statistics, the last raw snapshot
 * (what "save as evidence" persists) and a ring buffer of recent raw chunks so parsing
 * rules can be re-tested offline against real captured bytes. Every raw chunk, parsed
 * reading, status change and error is published as one event map to the sink (the SSE hub).
 */
public class CaptureSessionManager {

    public record RawSnapshot(String ascii, String visibleAscii, String hex, byte[] bytes, long sequence) { }

    private static final int RAW_HISTORY_CHUNKS = 500;

    private final Consumer<Map<String, Object>> sink;
    private final Object lock = new Object();
    private final Deque<byte[]> rawHistory = new ArrayDeque<>();
    private final AtomicReference<RawSnapshot> lastRaw = new AtomicReference<>();
    private final AtomicReference<NormalizedWeightReading> lastReading = new AtomicReference<>();
    private CaptureStatistics statistics = new CaptureStatistics();
    private volatile ParsePipeline pipeline;
    private IndicatorTransport transport;
    private IndicatorProfile connectedProfile;
    private String captureSessionId;
    private long rawSequence;
    private volatile String connectionState = "DISCONNECTED";
    private volatile String lastError;

    public CaptureSessionManager(Consumer<Map<String, Object>> sink) {
        this.sink = sink;
    }

    public void connect(IndicatorProfile profile) throws Exception {
        disconnect();
        IndicatorTransport newTransport = buildTransport(profile);
        synchronized (lock) {
            transport = newTransport;
            connectedProfile = profile;
            pipeline = new ParsePipeline(profile, "capture");
            statistics = new CaptureStatistics();
            captureSessionId = UUID.randomUUID().toString();
            rawHistory.clear();
            lastRaw.set(null);
            lastReading.set(null);
            lastError = null;
            connectionState = "CONNECTING";
        }
        newTransport.setListener(new SessionListener(newTransport));
        publishStatus("CONNECTING", "Connecting to " + newTransport.describe());
        try {
            newTransport.connect();
        } catch (Exception e) {
            synchronized (lock) {
                connectionState = "DISCONNECTED";
                lastError = e.getMessage();
                transport = null;
            }
            publish(Map.of("type", "error", "message", String.valueOf(e.getMessage())));
            publishStatus("DISCONNECTED", "Connection failed");
            throw e;
        }
        if (profile.getReadMode() == ReadMode.COMMAND_QUERY && profile.getQueryCommand() != null
                && !profile.getQueryCommand().isEmpty()) {
            newTransport.send(profile.getQueryCommand().getBytes(StandardCharsets.UTF_8));
        }
    }

    public void disconnect() {
        IndicatorTransport current;
        synchronized (lock) {
            current = transport;
            transport = null;
        }
        if (current != null) {
            current.disconnect();
        }
        connectionState = "DISCONNECTED";
    }

    public boolean isConnected() {
        IndicatorTransport current = transport;
        return current != null && current.isConnected();
    }

    public String connectionState() {
        return connectionState;
    }

    public String lastError() {
        return lastError;
    }

    public IndicatorProfile connectedProfile() {
        return connectedProfile;
    }

    public String captureSessionId() {
        return captureSessionId;
    }

    public CaptureStatistics statistics() {
        return statistics;
    }

    public void resetStatistics() {
        statistics.reset();
        publishStatistics();
    }

    public RawSnapshot lastRawSnapshot() {
        return lastRaw.get();
    }

    public NormalizedWeightReading lastReading() {
        return lastReading.get();
    }

    /** Hot-swaps framing/extraction/stability rules on the live connection. The
     *  hardware connection itself is untouched; only the parsing changes. */
    public void applyRules(IndicatorProfile rules) {
        synchronized (lock) {
            pipeline = new ParsePipeline(rules, "capture");
            statistics = new CaptureStatistics();
        }
        publish(Map.of("type", "rules", "message", "Parsing rules applied to live stream; counters reset"));
        publishStatistics();
    }

    public void requestReading() throws Exception {
        IndicatorTransport current = transport;
        IndicatorProfile profile = connectedProfile;
        if (current == null || !current.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        if (profile.getReadMode() != ReadMode.COMMAND_QUERY) {
            throw new IllegalStateException("Request reading only applies to Command / Query read mode");
        }
        if (profile.getQueryCommand() == null || profile.getQueryCommand().isEmpty()) {
            throw new IllegalStateException("No query command configured on this profile");
        }
        current.send(profile.getQueryCommand().getBytes(StandardCharsets.UTF_8));
        publish(Map.of("type", "sent", "ascii", Escapes.escape(profile.getQueryCommand())));
    }

    public void sendRaw(String text) throws Exception {
        IndicatorTransport current = transport;
        if (current == null || !current.isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        current.send(text.getBytes(StandardCharsets.UTF_8));
        publish(Map.of("type", "sent", "ascii", Escapes.escape(text)));
    }

    /** Snapshot copy of the recent raw chunks, oldest first. */
    public List<byte[]> rawHistory() {
        synchronized (lock) {
            return new ArrayList<>(rawHistory);
        }
    }

    private IndicatorTransport buildTransport(IndicatorProfile profile) {
        if (profile.getConnectionType() == ConnectionType.TCP) {
            return new TcpClientIndicatorTransport(
                    profile.getTcpHost(),
                    profile.getTcpPort() != null ? profile.getTcpPort() : 0,
                    profile.getConnectTimeoutMs() != null ? profile.getConnectTimeoutMs() : 10000,
                    profile.getReadTimeoutMs() != null ? profile.getReadTimeoutMs() : 5000);
        }
        return new SerialIndicatorTransport(
                profile.getSerialPort(),
                profile.getBaudRate() != null ? profile.getBaudRate() : 9600,
                profile.getDataBits() != null ? profile.getDataBits() : 8,
                profile.getStopBits() != null ? profile.getStopBits() : 1,
                profile.getParity(),
                profile.getFlowControl());
    }

    private void publish(Map<String, Object> event) {
        sink.accept(event);
    }

    private void publishStatus(String status, String detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "status");
        event.put("status", status);
        event.put("detail", detail);
        publish(event);
    }

    public void publishStatistics() {
        publish(statisticsEvent());
    }

    public Map<String, Object> statisticsEvent() {
        CaptureStatistics current = statistics;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "statistics");
        event.put("connected", isConnected());
        event.put("connectionState", connectionState);
        event.put("messagesReceived", current.getMessagesReceived());
        event.put("valid", current.getValidCount());
        event.put("invalid", current.getInvalidCount());
        event.put("stable", current.getStableCount());
        event.put("unstable", current.getUnstableCount());
        event.put("communicationErrors", current.getCommunicationErrorCount());
        ParsePipeline current2 = pipeline;
        event.put("pendingBytes", current2 != null ? current2.pendingBytes() : 0);
        return event;
    }

    public static Map<String, Object> readingEvent(NormalizedWeightReading reading, String rawMessage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "parsed");
        BigDecimal weight = reading.getWeight();
        event.put("weight", weight != null ? weight.toPlainString() : null);
        List<String> values = new ArrayList<>();
        for (BigDecimal value : reading.getValues()) {
            values.add(value.toPlainString());
        }
        event.put("values", values);
        event.put("unit", reading.getUnit());
        event.put("stability", reading.getStability().name());
        event.put("statusToken", reading.getStatusToken());
        event.put("rejectionReason", reading.getRejectionReason());
        event.put("rawMessage", rawMessage != null ? Escapes.escape(rawMessage) : null);
        event.put("timestamp", reading.getTimestamp().toString());
        return event;
    }

    private final class SessionListener implements TransportListener {
        private final IndicatorTransport owner;

        private SessionListener(IndicatorTransport owner) {
            this.owner = owner;
        }

        private boolean stale() {
            return transport != owner;
        }

        @Override
        public void onConnected() {
            if (stale()) {
                return;
            }
            connectionState = "CONNECTED";
            ParsePipeline current = pipeline;
            if (current != null) {
                current.reset();
            }
            publishStatus("CONNECTED", "Connected to " + owner.describe());
            publishStatistics();
        }

        @Override
        public void onData(byte[] chunk, int length) {
            if (stale()) {
                return;
            }
            byte[] exact = Arrays.copyOf(chunk, length);
            long sequence;
            synchronized (lock) {
                rawSequence++;
                sequence = rawSequence;
                rawHistory.addLast(exact);
                while (rawHistory.size() > RAW_HISTORY_CHUNKS) {
                    rawHistory.removeFirst();
                }
            }
            String ascii = new String(exact, StandardCharsets.ISO_8859_1);
            String visible = Escapes.visibleAscii(exact, exact.length);
            String hex = Escapes.hex(exact, exact.length);
            lastRaw.set(new RawSnapshot(ascii, visible, hex, exact, sequence));

            Map<String, Object> rawEvent = new LinkedHashMap<>();
            rawEvent.put("type", "raw");
            rawEvent.put("sequence", sequence);
            rawEvent.put("ascii", visible);
            rawEvent.put("hex", hex);
            rawEvent.put("length", exact.length);
            publish(rawEvent);

            ParsePipeline current = pipeline;
            if (current == null) {
                return;
            }
            for (ParsePipeline.Event event : current.feed(exact)) {
                statistics.record(event.reading());
                lastReading.set(event.reading());
                publish(readingEvent(event.reading(), event.rawMessage()));
            }
            publishStatistics();
        }

        @Override
        public void onDisconnected(boolean wasExpected) {
            if (stale() && wasExpected) {
                return;
            }
            connectionState = wasExpected ? "DISCONNECTED" : "CONNECTION_LOST";
            if (!wasExpected) {
                ParsePipeline current = pipeline;
                if (current != null) {
                    current.reset();
                }
                statistics.record(NormalizedWeightReading.communicationError("capture", null));
            }
            publishStatus(connectionState, wasExpected ? "Disconnected by technician" : "Connection lost");
            publishStatistics();
        }

        @Override
        public void onError(Exception e) {
            if (stale()) {
                return;
            }
            lastError = e.getMessage();
            publish(Map.of("type", "error", "message", String.valueOf(e.getMessage())));
        }
    }
}
