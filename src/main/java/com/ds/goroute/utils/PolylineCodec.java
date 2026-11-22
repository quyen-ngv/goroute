package com.ds.goroute.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Google's encoded polyline format, at any precision: 5 decimals is what Goong and Google
 * return, 6 is what Mapbox returns with {@code geometries=polyline6} and what the server stores.
 * Points are {latitude, longitude}.
 */
public final class PolylineCodec {

    private PolylineCodec() {
    }

    public static List<double[]> decode(String encoded, int precision) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }
        double factor = Math.pow(10, precision);
        int index = 0;
        long latitude = 0;
        long longitude = 0;
        while (index < encoded.length()) {
            long[] next = readValue(encoded, index);
            latitude += next[0];
            index = (int) next[1];
            next = readValue(encoded, index);
            longitude += next[0];
            index = (int) next[1];
            points.add(new double[]{latitude / factor, longitude / factor});
        }
        return points;
    }

    public static String encode(List<double[]> points, int precision) {
        double factor = Math.pow(10, precision);
        StringBuilder out = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (double[] point : points) {
            long latitude = Math.round(point[0] * factor);
            long longitude = Math.round(point[1] * factor);
            writeValue(out, latitude - previousLatitude);
            writeValue(out, longitude - previousLongitude);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return out.toString();
    }

    /** @return {value, next index} */
    private static long[] readValue(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        int chunk;
        do {
            chunk = encoded.charAt(index++) - 63;
            result |= (long) (chunk & 0x1f) << shift;
            shift += 5;
        } while (chunk >= 0x20 && index < encoded.length());
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[]{value, index};
    }

    private static void writeValue(StringBuilder out, long value) {
        long shifted = value < 0 ? ~(value << 1) : (value << 1);
        while (shifted >= 0x20) {
            out.append((char) ((0x20 | (shifted & 0x1f)) + 63));
            shifted >>= 5;
        }
        out.append((char) (shifted + 63));
    }
}
