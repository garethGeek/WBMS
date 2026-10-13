package wbms.tanroads.wbms.capture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SerialDataDecoder {

    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private final boolean detectControlFraming;
    private boolean skipLf = false;
    private boolean framed = false;

    public SerialDataDecoder() {
        this(true);
    }

    public SerialDataDecoder(boolean detectControlFraming) {
        this.detectControlFraming = detectControlFraming;
    }

    public List<byte[]> append(byte[] bytes, int length) {
        List<byte[]> frames = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            if (detectControlFraming && !framed && pending.size() == 0 && (value == 0x01 || value == 0x02)) {
                framed = true;
                continue;
            }
            if (detectControlFraming && framed && value == 0x03) {
                addPendingFrame(frames);
                framed = false;
                continue;
            }
            if (skipLf && value == '\n') {
                skipLf = false;
                continue;
            }
            skipLf = false;
            if (!framed && (value == '\r' || value == '\n')) {
                addPendingFrame(frames);
                skipLf = value == '\r';
            } else {
                pending.write(value);
            }
        }
        return frames;
    }

    public List<byte[]> flush() {
        List<byte[]> frames = new ArrayList<>();
        addPendingFrame(frames);
        return frames;
    }

    private void addPendingFrame(List<byte[]> frames) {
        if (pending.size() > 0) {
            frames.add(pending.toByteArray());
            pending.reset();
        }
    }

    public static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    public static String hex(byte[] bytes, int length) {
        StringBuilder result = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            if (i > 0) result.append(' ');
            result.append(String.format("%02X", bytes[i] & 0xff));
        }
        return result.toString();
    }

    public static String escaped(byte[] bytes, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            if (value == '\r') result.append("\\r");
            else if (value == '\n') result.append("\\n");
            else if (value == '\t') result.append("\\t");
            else if (value >= 32 && value <= 126) result.append((char) value);
            else result.append(String.format("\\x%02X", value));
        }
        return result.toString();
    }
}
