package com.tanroads.weighbridge.profilebuilder.engine;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.tanroads.weighbridge.profilebuilder.model.FramingMode;
import com.tanroads.weighbridge.profilebuilder.model.IndicatorProfile;

/**
 * Reassembles raw, arbitrarily fragmented byte chunks into complete messages per the
 * framing mode. Port of the main system class of the same name, so a delimiter / fixed
 * length that frames correctly here frames identically in production. Not thread safe:
 * one instance per connection, fed sequentially.
 */
public class MessagePacketizer {

    public enum Outcome { OK, BUFFER_OVERFLOW }

    public record Result(List<String> messages, Outcome outcome) { }

    private final IndicatorProfile profile;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public MessagePacketizer(IndicatorProfile profile) {
        this.profile = profile;
    }

    public Result accept(byte[] chunk) {
        buffer.write(chunk, 0, chunk.length);
        int maxBytes = profile.getMaxBufferBytes() != null ? profile.getMaxBufferBytes() : 4096;
        if (buffer.size() > maxBytes) {
            buffer.reset();
            return new Result(new ArrayList<>(), Outcome.BUFFER_OVERFLOW);
        }
        if (profile.getFramingMode() == FramingMode.FIXED_LENGTH) {
            return extractFixedLength();
        }
        return extractDelimited();
    }

    private Result extractFixedLength() {
        int length = profile.getFixedMessageLength() != null ? profile.getFixedMessageLength() : 0;
        List<String> messages = new ArrayList<>();
        if (length <= 0) {
            return new Result(messages, Outcome.OK);
        }
        byte[] current = buffer.toByteArray();
        int offset = 0;
        while (current.length - offset >= length) {
            messages.add(new String(current, offset, length, StandardCharsets.UTF_8));
            offset += length;
        }
        buffer.reset();
        if (offset < current.length) {
            buffer.write(current, offset, current.length - offset);
        }
        return new Result(messages, Outcome.OK);
    }

    private Result extractDelimited() {
        String delimiter = profile.getMessageDelimiter();
        List<String> messages = new ArrayList<>();
        if (delimiter == null || delimiter.isEmpty()) {
            return new Result(messages, Outcome.OK);
        }
        String accumulated = buffer.toString(StandardCharsets.UTF_8);
        int searchFrom = 0;
        int delimiterIndex;
        while ((delimiterIndex = accumulated.indexOf(delimiter, searchFrom)) != -1) {
            messages.add(accumulated.substring(searchFrom, delimiterIndex));
            searchFrom = delimiterIndex + delimiter.length();
        }
        String remainder = accumulated.substring(searchFrom);
        buffer.reset();
        if (!remainder.isEmpty()) {
            byte[] remainderBytes = remainder.getBytes(StandardCharsets.UTF_8);
            buffer.write(remainderBytes, 0, remainderBytes.length);
        }
        return new Result(messages, Outcome.OK);
    }

    /** Bytes currently held back as an incomplete trailing fragment (for the UI). */
    public int pendingBytes() {
        return buffer.size();
    }

    public void reset() {
        buffer.reset();
    }
}
