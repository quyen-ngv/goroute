package com.ds.goroute.service.track;

import java.util.ArrayList;
import java.util.List;

/**
 * Douglas–Peucker simplification in metres, on a local equirectangular projection (accurate
 * enough at the scale of one day's route). Coordinates are {@code [latitude, longitude]}.
 */
public final class LineSimplifier {

    private static final double EARTH_RADIUS_M = 6_371_000d;
    private static final double INITIAL_TOLERANCE_M = 3d;

    private LineSimplifier() {
    }

    /**
     * Simplifies with a growing tolerance until the line has at most {@code maxPoints}
     * points. The first and last points are always kept.
     */
    public static List<double[]> simplify(List<double[]> line, int maxPoints) {
        if (line.size() <= 2) {
            return line;
        }
        double tolerance = INITIAL_TOLERANCE_M;
        List<double[]> result = simplify(line, tolerance);
        while (result.size() > maxPoints) {
            tolerance *= 2;
            result = simplify(line, tolerance);
        }
        return result;
    }

    static List<double[]> simplify(List<double[]> line, double toleranceM) {
        double referenceLat = Math.toRadians(line.get(0)[0]);
        double[][] xy = new double[line.size()][];
        for (int i = 0; i < line.size(); i++) {
            double[] point = line.get(i);
            xy[i] = new double[] {
                    Math.toRadians(point[1]) * Math.cos(referenceLat) * EARTH_RADIUS_M,
                    Math.toRadians(point[0]) * EARTH_RADIUS_M};
        }
        boolean[] keep = new boolean[line.size()];
        keep[0] = true;
        keep[line.size() - 1] = true;
        // Iterative stack instead of recursion: a long trek can have thousands of points.
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[] {0, line.size() - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.remove(stack.size() - 1);
            int start = range[0];
            int end = range[1];
            double maxDistance = -1;
            int farthest = -1;
            for (int i = start + 1; i < end; i++) {
                double distance = perpendicularDistance(xy[i], xy[start], xy[end]);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    farthest = i;
                }
            }
            if (farthest >= 0 && maxDistance > toleranceM) {
                keep[farthest] = true;
                stack.add(new int[] {start, farthest});
                stack.add(new int[] {farthest, end});
            }
        }
        List<double[]> simplified = new ArrayList<>();
        for (int i = 0; i < line.size(); i++) {
            if (keep[i]) {
                simplified.add(line.get(i));
            }
        }
        return simplified;
    }

    private static double perpendicularDistance(double[] point, double[] start, double[] end) {
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return Math.hypot(point[0] - start[0], point[1] - start[1]);
        }
        double t = ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(point[0] - (start[0] + t * dx), point[1] - (start[1] + t * dy));
    }
}
