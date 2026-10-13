package wbms.tanroads.wbms.capture;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SerialDataDecoderTest {

    @Test
    void splitsCrLfCrAndLfFrames() {
        SerialDataDecoder decoder = new SerialDataDecoder();

        List<byte[]> frames = decoder.append("a\r\nb\rc\n".getBytes(StandardCharsets.UTF_8), 7);

        assertEquals(List.of("a", "b", "c"), frames.stream().map(SerialDataDecoder::text).toList());
    }

    @Test
    void flushesUnterminatedFrame() {
        SerialDataDecoder decoder = new SerialDataDecoder();

        decoder.append("00, 00, 00, 100".getBytes(StandardCharsets.UTF_8), 15);

        assertEquals(List.of("00, 00, 00, 100"), decoder.flush().stream().map(SerialDataDecoder::text).toList());
    }
}
