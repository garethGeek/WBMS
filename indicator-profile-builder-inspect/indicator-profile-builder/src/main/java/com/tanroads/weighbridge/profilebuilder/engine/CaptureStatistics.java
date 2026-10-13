package com.tanroads.weighbridge.profilebuilder.engine;

import java.util.concurrent.atomic.AtomicLong;

/** Running counters for one capture session. Thread safe: readings arrive on the
 *  transport thread while the browser polls from an HTTP thread. */
public class CaptureStatistics {
    private final AtomicLong messagesReceived = new AtomicLong();
    private final AtomicLong validCount = new AtomicLong();
    private final AtomicLong invalidCount = new AtomicLong();
    private final AtomicLong stableCount = new AtomicLong();
    private final AtomicLong unstableCount = new AtomicLong();
    private final AtomicLong communicationErrorCount = new AtomicLong();

    public void record(NormalizedWeightReading reading) {
        messagesReceived.incrementAndGet();
        switch (reading.getStability()) {
            case STABLE -> { validCount.incrementAndGet(); stableCount.incrementAndGet(); }
            case UNSTABLE -> { validCount.incrementAndGet(); unstableCount.incrementAndGet(); }
            case INVALID -> invalidCount.incrementAndGet();
            case COMMUNICATION_ERROR -> communicationErrorCount.incrementAndGet();
            default -> { }
        }
    }

    public void reset() {
        messagesReceived.set(0);
        validCount.set(0);
        invalidCount.set(0);
        stableCount.set(0);
        unstableCount.set(0);
        communicationErrorCount.set(0);
    }

    public long getMessagesReceived() { return messagesReceived.get(); }
    public long getValidCount() { return validCount.get(); }
    public long getInvalidCount() { return invalidCount.get(); }
    public long getStableCount() { return stableCount.get(); }
    public long getUnstableCount() { return unstableCount.get(); }
    public long getCommunicationErrorCount() { return communicationErrorCount.get(); }
}
