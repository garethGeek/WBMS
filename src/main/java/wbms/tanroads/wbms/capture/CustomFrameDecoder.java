package wbms.tanroads.wbms.capture;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Extracts complete byte frames for a live, unsaved profile. */
public final class CustomFrameDecoder {
    private final byte[] start;
    private final byte[] end;
    private final int maxBytes;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private boolean collecting;

    public CustomFrameDecoder(String startHex, String endHex) {
        this.start = parseHex(startHex);
        this.end = parseHex(endHex);
        this.maxBytes = 4096;
    }

    public List<byte[]> append(byte[] bytes, int length) {
        List<byte[]> frames = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            if (!collecting) {
                if (matches(bytes, i, length, start)) {
                    pending.reset();
                    write(start);
                    collecting = true;
                    i += start.length - 1;
                }
                continue;
            }
            pending.write(value);
            if (pending.size() > maxBytes) {
                pending.reset();
                collecting = false;
                continue;
            }
            if (endsWith(pending.toByteArray(), end)) {
                frames.add(pending.toByteArray());
                pending.reset();
                collecting = false;
            }
        }
        return frames;
    }

    public List<byte[]> flush() {
        return List.of();
    }

    private void write(byte[] bytes) { pending.writeBytes(bytes); }
    private static boolean endsWith(byte[] value, byte[] suffix) {
        if (suffix.length == 0 || value.length < suffix.length) return false;
        for (int i = 1; i <= suffix.length; i++) if (value[value.length - i] != suffix[suffix.length - i]) return false;
        return true;
    }
    private static boolean matches(byte[] value, int offset, int length, byte[] target) {
        if (target.length == 0 || offset + target.length > length) return false;
        for (int i = 0; i < target.length; i++) if ((value[offset + i] & 0xff) != (target[i] & 0xff)) return false;
        return true;
    }
    public static byte[] parseHex(String value) {
        if (value == null || value.isBlank()) return new byte[0];
        String hex = value.replaceAll("[^0-9A-Fa-f]", "");
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("HEX byte values must contain pairs of digits");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
