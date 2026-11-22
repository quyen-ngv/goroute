package com.ds.goroute.service.track;

import com.ds.goroute.utils.GeoDistance;

import java.util.List;

/** Small geometry helpers shared by the track classes. */
final class TrackGeo {

    private TrackGeo() {
    }

    static double metres(TrackPoint from, TrackPoint to) {
        return GeoDistance.between(from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    static double metres(double fromLat, double fromLng, double toLat, double toLng) {
        return GeoDistance.between(fromLat, fromLng, toLat, toLng);
    }

    /** {@code [minLng, minLat, maxLng, maxLat]}, or null for no coordinates. */
    static double[] bbox(List<double[]> latLngs) {
        if (latLngs.isEmpty()) {
            return null;
        }
        double minLat = Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;
        for (double[] point : latLngs) {
            minLat = Math.min(minLat, point[0]);
            maxLat = Math.max(maxLat, point[0]);
            minLng = Math.min(minLng, point[1]);
            maxLng = Math.max(maxLng, point[1]);
        }
        return new double[] {minLng, minLat, maxLng, maxLat};
    }
}
