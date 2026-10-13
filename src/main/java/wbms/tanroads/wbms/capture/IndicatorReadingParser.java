package wbms.tanroads.wbms.capture;

import wbms.tanroads.wbms.enums.WeighbridgeProtocolType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts model-specific serial text into WBMS kilograms. */
public final class IndicatorReadingParser {

    private static final Pattern YAOHUA_TOKEN = Pattern.compile("([+-]?\\d+\\.\\d+)(?:=|$)");

    private IndicatorReadingParser() {
    }

    public static Double parseWeightKg(String raw, WeighbridgeProtocolType protocol) {
        if (raw == null || raw.isBlank()) return null;
        if (protocol == WeighbridgeProtocolType.YAOHUA_XK3190) {
            Matcher matcher = YAOHUA_TOKEN.matcher(raw.trim());
            if (matcher.find()) {
                String reversed = new StringBuilder(matcher.group(1)).reverse().toString();
                try {
                    return Double.parseDouble(reversed);
                } catch (NumberFormatException ignored) {
                    // Fall through to the generic parser for non-D2+ output.
                }
            }
        }
        return WeightParser.parseWeightKg(raw);
    }
}
