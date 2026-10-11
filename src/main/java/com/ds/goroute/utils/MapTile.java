package com.ds.goroute.utils;

/**
 * One slippy-map tile, the Web Mercator scheme Mapbox and Google Maps draw with: zoom {@code z}
 * splits the world into 2^z × 2^z tiles, {@code x} counted east from the antimeridian and
 * {@code y} south from 85.05°N.
 */
public record MapTile(int z, int x, int y) {

    public static final int MAX_ZOOM = 22;

    /** Web Mercator stops here; the poles are not on the map. */
    public static final double MAX_LATITUDE = 85.05112878;

    public MapTile {
        if (!isValid(z, x, y)) {
            throw new IllegalArgumentException("No tile " + z + "/" + x + "/" + y);
        }
    }

    public static boolean isValid(int z, int x, int y) {
        if (z < 0 || z > MAX_ZOOM) {
            return false;
        }
        long tiles = 1L << z;
        return x >= 0 && x < tiles && y >= 0 && y < tiles;
    }

    /** The tile of zoom {@code z} the point lies in. */
    public static MapTile containing(double latitude, double longitude, int z) {
        long tiles = 1L << z;
        int x = (int) Math.max(0, Math.min(tiles - 1, (long) Math.floor(unitX(longitude) * tiles)));
        int y = (int) Math.max(0, Math.min(tiles - 1, (long) Math.floor(unitY(latitude) * tiles)));
        return new MapTile(z, x, y);
    }

    public double west() {
        return longitudeOf((double) x / tilesAcross());
    }

    public double east() {
        return longitudeOf((double) (x + 1) / tilesAcross());
    }

    public double north() {
        return latitudeOf((double) y / tilesAcross());
    }

    public double south() {
        return latitudeOf((double) (y + 1) / tilesAcross());
    }

    private long tilesAcross() {
        return 1L << z;
    }

    /** Position east in a 1-wide world, from 0 at the antimeridian. */
    public static double unitX(double longitude) {
        return (longitude + 180.0) / 360.0;
    }

    /** Position south in a 1-high world, from 0 at {@link #MAX_LATITUDE}. */
    public static double unitY(double latitude) {
        double radians = Math.toRadians(Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, latitude)));
        return (1.0 - Math.log(Math.tan(radians) + 1.0 / Math.cos(radians)) / Math.PI) / 2.0;
    }

    private static double longitudeOf(double unitX) {
        return unitX * 360.0 - 180.0;
    }

    private static double latitudeOf(double unitY) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * unitY))));
    }
}
