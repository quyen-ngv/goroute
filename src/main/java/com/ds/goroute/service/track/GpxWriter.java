package com.ds.goroute.service.track;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Writes a GPX 1.1 document (https://www.topografix.com/GPX/1/1/): one {@code <trk>}, one
 * {@code <trkseg>} per day. Times are UTC with a trailing {@code Z}; a point without a time
 * or an altitude (a day whose raw points expired, drawn from its simplified line) simply
 * omits that element, which the schema allows.
 */
public final class GpxWriter {

    private GpxWriter() {
    }

    /**
     * @param altitudeM null when unknown
     * @param timeMs    epoch millis, null when unknown
     */
    public record Point(double latitude, double longitude, Integer altitudeM, Long timeMs) {
    }

    public static String write(String trackName, List<List<Point>> days) {
        StringBuilder out = new StringBuilder(256 + days.size() * 4096);
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<gpx version=\"1.1\" creator=\"Vietdetour\" xmlns=\"http://www.topografix.com/GPX/1/1\"")
                .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1")
                .append(" http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
                .append("  <trk>\n")
                .append("    <name>").append(escape(trackName)).append("</name>\n");
        for (List<Point> day : days) {
            out.append("    <trkseg>\n");
            for (Point point : day) {
                out.append("      <trkpt lat=\"").append(coordinate(point.latitude()))
                        .append("\" lon=\"").append(coordinate(point.longitude())).append('"');
                if (point.altitudeM() == null && point.timeMs() == null) {
                    out.append("/>\n");
                    continue;
                }
                out.append('>');
                if (point.altitudeM() != null) {
                    out.append("<ele>").append(point.altitudeM()).append("</ele>");
                }
                if (point.timeMs() != null) {
                    out.append("<time>").append(Instant.ofEpochMilli(point.timeMs())).append("</time>");
                }
                out.append("</trkpt>\n");
            }
            out.append("    </trkseg>\n");
        }
        out.append("  </trk>\n</gpx>\n");
        return out.toString();
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    // Characters XML 1.0 forbids outright are dropped rather than escaped.
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.7f", value);
    }
}
