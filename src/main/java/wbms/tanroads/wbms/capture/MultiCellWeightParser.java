package wbms.tanroads.wbms.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultiCellWeightParser {

    public record MultiCellReading(List<Double> cellWeightsKg, Double indicatorGvmKg) {}

    private static final Pattern CELL_VALUE = Pattern.compile("(?<![A-Za-z])[-+]?\\d+(?:\\.\\d+)?(?![A-Za-z])");
    private static final Pattern KG_DELIMITED_CELL_VALUE = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?\\s*(?=kg\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABELED_CELL_VALUE = Pattern.compile(":\\s*([-+]?\\d+(?:\\.\\d+)?)");

    private MultiCellWeightParser() {
    }

    public static List<Double> parseLatest(String raw, int cellCount) {
        return parseLatest(raw, cellCount, null);
    }

    public static List<Double> parseLatest(String raw, int cellCount, String configuredSeparator) {
        return parseLatestInternal(raw, cellCount, configuredSeparator, true);
    }

    public static List<Double> parseLatestStrict(String raw, int cellCount, String configuredSeparator) {
        return parseLatestInternal(raw, cellCount, configuredSeparator, false);
    }

    /**
     * Parses a multi-cell frame where an optional value after all cells is the
     * indicator-supplied GVM. The existing parseLatest methods intentionally
     * keep their historical behavior for callers that do not need GVM metadata.
     */
    public static MultiCellReading parseWithGvm(String raw, int cellCount, String configuredSeparator, boolean strict) {
        if (raw == null || cellCount <= 0) return new MultiCellReading(List.of(), null);

        String separator = normalizeSeparator(configuredSeparator);
        List<Double> values = List.of();
        if (separator != null && !separator.isEmpty() && raw.contains(separator)) {
            values = parseSeparated(raw, separator);
        } else if (strict && separator != null && !separator.isEmpty()) {
            return new MultiCellReading(List.of(), null);
        } else if (!strict && cellCount > 1 && raw.split("049", -1).length > 1) {
            values = parseSeparated(raw, "049");
        }

        if (values.isEmpty()) {
            values = parseNumericValues(raw);
        }
        if (values.size() < cellCount) return new MultiCellReading(List.of(), null);

        if (values.size() > cellCount) {
            int start = values.size() - cellCount - 1;
            return new MultiCellReading(
                    List.copyOf(values.subList(start, start + cellCount)),
                    values.get(values.size() - 1));
        }
        int start = values.size() - cellCount;
        return new MultiCellReading(List.copyOf(values.subList(start, values.size())), null);
    }

    private static List<Double> parseLatestInternal(String raw, int cellCount, String configuredSeparator, boolean allowLegacySeparator) {
        if (raw == null || cellCount <= 0) return List.of();

        String separator = normalizeSeparator(configuredSeparator);
        if (cellCount > 1 && separator != null && !separator.isEmpty() && raw.contains(separator)) {
            List<Double> separated = parseSeparated(raw, separator);
            if (separated.size() >= cellCount) return List.copyOf(separated.subList(separated.size() - cellCount, separated.size()));
            if (!allowLegacySeparator) return List.of();
        } else if (cellCount > 1 && separator != null && !separator.isEmpty() && !allowLegacySeparator) {
            return List.of();
        }

        // TANROADS multi-scale frames commonly use the literal ASCII separator "049".
        if (allowLegacySeparator && cellCount > 1 && raw.split("049", -1).length > 1) {
            List<Double> separated = parseSeparated(raw, "049");
            if (separated.size() >= cellCount) return List.copyOf(separated.subList(separated.size() - cellCount, separated.size()));
        }

        List<Double> values = parseNumericValues(raw);
        if (values.size() < cellCount) return List.of();
        return List.copyOf(values.subList(values.size() - cellCount, values.size()));
    }

    private static List<Double> parseNumericValues(String raw) {
        List<Double> values = new ArrayList<>();
        Matcher matcher = raw.contains(":")
                ? LABELED_CELL_VALUE.matcher(raw)
                : KG_DELIMITED_CELL_VALUE.matcher(raw).find()
                    ? KG_DELIMITED_CELL_VALUE.matcher(raw)
                    : CELL_VALUE.matcher(raw);
        while (matcher.find()) {
            try {
                String token = raw.contains(":") ? matcher.group(1) : matcher.group();
                values.add(Double.valueOf(token));
            } catch (NumberFormatException ignored) {
                // Ignore malformed numeric tokens and keep valid cell readings.
            }
        }
        return values;
    }

    private static List<Double> parseSeparated(String raw, String separator) {
            List<Double> separated = new ArrayList<>();
            for (String part : raw.split(Pattern.quote(separator), -1)) {
                Matcher number = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?").matcher(part);
                String last = null;
                while (number.find()) last = number.group();
                if (last != null) {
                    try { separated.add(Double.valueOf(last)); } catch (NumberFormatException ignored) { }
                }
            }
            return separated;
    }

    private static String normalizeSeparator(String separator) {
        if (separator == null || separator.isBlank()) return null;
        if (separator.trim().equalsIgnoreCase("SPACE") || separator.equals("\\s")) return " ";
        if (separator.trim().equalsIgnoreCase("TAB") || separator.equals("\\t") || separator.equalsIgnoreCase("0x09")) return "\t";
        return separator;
    }
}
