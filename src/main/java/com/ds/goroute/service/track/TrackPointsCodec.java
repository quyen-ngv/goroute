package com.ds.goroute.service.track;

import com.ds.goroute.dto.TrackPointsPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the columnar wire format and {@link TrackPoint}s. Pure; every structural
 * problem becomes an {@link IllegalArgumentException} whose message names the problem, which
 * the service turns into {@code TRACK_CHUNK_INVALID}.
 */
public final class TrackPointsCodec {

    private static final double E7 = 1e7;

    private TrackPointsCodec() {
    }

    public static List<TrackPoint> decode(TrackPointsPayload payload, int maxPoints) {
        if (payload == null || payload.getT0() == null || payload.getDt() == null
                || payload.getLat() == null || payload.getLng() == null) {
            throw new IllegalArgumentException("t0, dt, lat and lng are required");
        }
        if (payload.getV() != null && payload.getV() != TrackPointsPayload.FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported points format v=" + payload.getV());
        }
        int size = payload.getDt().size();
        if (size == 0) {
            throw new IllegalArgumentException("no points");
        }
        if (size > maxPoints) {
            throw new IllegalArgumentException("too many points: " + size + " > " + maxPoints);
        }
        requireSize("lat", payload.getLat(), size);
        requireSize("lng", payload.getLng(), size);
        requireOptionalSize("acc", payload.getAcc(), size);
        requireOptionalSize("alt", payload.getAlt(), size);
        requireOptionalSize("spd", payload.getSpd(), size);

        List<TrackPoint> points = new ArrayList<>(size);
        long time = payload.getT0();
        for (int i = 0; i < size; i++) {
            Long step = payload.getDt().get(i);
            if (step == null) {
                throw new IllegalArgumentException("dt[" + i + "] is null");
            }
            if (i == 0 && step != 0) {
                throw new IllegalArgumentException("dt[0] must be 0");
            }
            if (i > 0 && step <= 0) {
                throw new IllegalArgumentException("time must strictly increase at index " + i);
            }
            time += step;
            Long lat = payload.getLat().get(i);
            Long lng = payload.getLng().get(i);
            if (lat == null || lng == null) {
                throw new IllegalArgumentException("lat/lng[" + i + "] is null");
            }
            points.add(new TrackPoint(
                    time,
                    lat / E7,
                    lng / E7,
                    at(payload.getAcc(), i),
                    at(payload.getAlt(), i),
                    at(payload.getSpd(), i)));
        }
        return points;
    }

    /** Encodes points that are already sorted by time with strictly increasing timestamps. */
    public static TrackPointsPayload encode(List<TrackPoint> points) {
        if (points.isEmpty()) {
            return null;
        }
        List<Long> dt = new ArrayList<>(points.size());
        List<Long> lat = new ArrayList<>(points.size());
        List<Long> lng = new ArrayList<>(points.size());
        List<Integer> acc = new ArrayList<>(points.size());
        List<Integer> alt = new ArrayList<>(points.size());
        List<Integer> spd = new ArrayList<>(points.size());
        long previous = points.get(0).timeMs();
        for (TrackPoint point : points) {
            dt.add(point.timeMs() - previous);
            previous = point.timeMs();
            lat.add(Math.round(point.latitude() * E7));
            lng.add(Math.round(point.longitude() * E7));
            acc.add(point.accuracyM());
            alt.add(point.altitudeM());
            spd.add(point.speedDms());
        }
        return TrackPointsPayload.builder()
                .v(TrackPointsPayload.FORMAT_VERSION)
                .t0(points.get(0).timeMs())
                .dt(dt)
                .lat(lat)
                .lng(lng)
                .acc(acc)
                .alt(alt)
                .spd(spd)
                .build();
    }

    private static void requireSize(String name, List<?> values, int size) {
        if (values.size() != size) {
            throw new IllegalArgumentException(name + " has " + values.size() + " values, expected " + size);
        }
    }

    private static void requireOptionalSize(String name, List<?> values, int size) {
        if (values != null) {
            requireSize(name, values, size);
        }
    }

    private static Integer at(List<Integer> values, int index) {
        return values == null ? null : values.get(index);
    }
}
