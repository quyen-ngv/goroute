package com.ds.goroute.service.track;

import java.util.ArrayList;
import java.util.List;

/**
 * Google's encoded polyline algorithm at precision 5 (about 1.1 m), the format the app and
 * Mapbox tooling already understand. Coordinates are {@code [latitude, longitude]} pairs.
 */
public final class PolylineCodec {

    private static final double FACTOR = 1e5;

    private PolylineCodec() {
    }

    public static String encode(List<double[]> coordinates) {
        StringBuilder out = new StringBuilder();
        long previousLat = 0;
        long previousLng = 0;
        for (double[] coordinate : coordinates) {
            long lat = Math.round(coordinate[0] * FACTOR);
            long lng = Math.round(coordinate[1] * FACTOR);
            encodeValue(lat - previousLat, out);
            encodeValue(lng - previousLng, out);
            previousLat = lat;
            previousLng = lng;
        }
        return out.toString();
    }

    public static List<double[]> decode(String encoded) {
        List<double[]> coordinates = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lng = 0;
        while (index < encoded.length()) {
            long[] result = decodeValue(encoded, index);
            lat += result[0];
            index = (int) result[1];
            result = decodeValue(encoded, index);
            lng += result[0];
            index = (int) result[1];
            coordinates.add(new double[] {lat / FACTOR, lng / FACTOR});
        }
        return coordinates;
    }

    private static void encodeValue(long value, StringBuilder out) {
        long shifted = value < 0 ? ~(value << 1) : value << 1;
        while (shifted >= 0x20) {
            out.append((char) ((0x20 | (shifted & 0x1f)) + 63));
            shifted >>= 5;
        }
        out.append((char) (shifted + 63));
    }

    /** Returns {delta, nextIndex}. */
    private static long[] decodeValue(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        int chunk;
        do {
            chunk = encoded.charAt(index++) - 63;
            result |= (long) (chunk & 0x1f) << shift;
            shift += 5;
        } while (chunk >= 0x20);
        long delta = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
        return new long[] {delta, index};
    }
}
