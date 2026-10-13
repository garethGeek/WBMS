package com.tanroads.weighbridge.profilebuilder.service;

/**
 * Converts between the REAL control characters a profile stores (a real CR LF in
 * messageDelimiter, exactly what the main system packetizer searches for) and the readable
 * escaped form shown in the web UI (the four characters backslash r backslash n).
 *
 * Supported escapes: \r \n \t \0 \\ \xHH (one byte in hex, e.g. \x02 for STX, \x03 for ETX).
 */
public final class Escapes {

    private static final char BACKSLASH = (char) 92;
    private static final char CR = (char) 13;
    private static final char LF = (char) 10;
    private static final char TAB = (char) 9;
    private static final char NUL = (char) 0;

    private Escapes() {
    }

    /** UI text -> real characters. Null/empty passes through unchanged. */
    public static String unescape(String text) {
        if (text == null || text.indexOf(BACKSLASH) < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != BACKSLASH || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(i + 1);
            switch (next) {
                case 'r' -> { out.append(CR); i++; }
                case 'n' -> { out.append(LF); i++; }
                case 't' -> { out.append(TAB); i++; }
                case '0' -> { out.append(NUL); i++; }
                case 'x' -> {
                    if (i + 3 < text.length()) {
                        try {
                            out.append((char) Integer.parseInt(text.substring(i + 2, i + 4), 16));
                            i += 3;
                            continue;
                        } catch (NumberFormatException ignored) {
                            // not a hex escape: keep the backslash literally
                        }
                    }
                    out.append(c);
                }
                default -> {
                    if (next == BACKSLASH) {
                        out.append(BACKSLASH);
                        i++;
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Real characters -> readable UI text. Printable ASCII is left as is; everything
     *  else becomes an escape so a technician can see exactly what is configured. */
    public static String escape(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == CR) {
                out.append(BACKSLASH).append('r');
            } else if (c == LF) {
                out.append(BACKSLASH).append('n');
            } else if (c == TAB) {
                out.append(BACKSLASH).append('t');
            } else if (c == NUL) {
                out.append(BACKSLASH).append('0');
            } else if (c == BACKSLASH) {
                out.append(BACKSLASH).append(BACKSLASH);
            } else if (c < 0x20 || c == 0x7F) {
                out.append(BACKSLASH).append(String.format("x%02X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Renders raw bytes for the RAW panel: printable ASCII kept, control bytes shown as
     *  visible escapes, so CR LF framing is visible instead of invisible. */
    public static String visibleAscii(byte[] data, int length) {
        StringBuilder out = new StringBuilder(length + 8);
        for (int i = 0; i < length; i++) {
            int b = data[i] & 0xFF;
            switch (b) {
                case 0x0D -> out.append(BACKSLASH).append('r');
                case 0x0A -> out.append(BACKSLASH).append('n');
                case 0x09 -> out.append(BACKSLASH).append('t');
                case 0x02 -> out.append("<STX>");
                case 0x03 -> out.append("<ETX>");
                default -> {
                    if (b < 0x20 || b >= 0x7F) {
                        out.append(BACKSLASH).append(String.format("x%02X", b));
                    } else {
                        out.append((char) b);
                    }
                }
            }
        }
        return out.toString();
    }

    public static String hex(byte[] data, int length) {
        StringBuilder hex = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                hex.append(' ');
            }
            hex.append(String.format("%02X", data[i] & 0xFF));
        }
        return hex.toString();
    }
}
