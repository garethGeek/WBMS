package wbms.tanroads.wbms.capture;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeightParser {

    // Example matches: "  12345 kg", "12345.50", "WT: 1200"
    private static final Pattern NUMBER = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");

    public static Double parseWeightKg(String raw) {
        if (raw == null) return null;
        Matcher m = NUMBER.matcher(raw);
        String lastMatch = null;
        while (m.find()) {
            lastMatch = m.group(1);
        }
        if (lastMatch != null) {
            try {
                return Double.parseDouble(lastMatch);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
