package com.ds.goroute.quest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

/**
 * The search circle of an AREA checkpoint (§3.14.1). Its centre is offset from the real spot so the
 * circle does not point at it, by at most {@code 0.6 × (searchRadius − unlockRadius)}, which keeps
 * the whole unlock circle inside the search circle.
 *
 * <p>The offset is random but seeded by the quest id, the spot and both radii, so re-saving a
 * checkpoint whose location did not change lands on the same centre: an edit to its text never
 * makes the circle jump.
 */
public final class QuestSearchArea {

    /** Share of the free ring (search radius minus unlock radius) the centre may move. */
    static final double MAX_OFFSET_SHARE = 0.6;
    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private QuestSearchArea() {
    }

    public record Center(BigDecimal latitude, BigDecimal longitude) {
    }

    public static Center center(UUID questId, BigDecimal latitude, BigDecimal longitude,
                                int unlockRadiusM, int searchRadiusM) {
        String seedText = questId + "|" + plain(latitude) + "|" + plain(longitude)
                + "|" + unlockRadiusM + "|" + searchRadiusM;
        UUID seed = UUID.nameUUIDFromBytes(seedText.getBytes(StandardCharsets.UTF_8));
        Random random = new Random(seed.getMostSignificantBits() ^ seed.getLeastSignificantBits());

        double maxOffset = Math.max(0, MAX_OFFSET_SHARE * (searchRadiusM - unlockRadiusM));
        double bearing = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * maxOffset;

        double lat = latitude.doubleValue();
        double dLat = distance * Math.cos(bearing) / METERS_PER_DEGREE_LAT;
        double dLng = distance * Math.sin(bearing) / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
        // NUMERIC(9,6) is about 0.1 m; the 0.4 × ring left over is metres, so rounding cannot push
        // the spot out of the circle.
        return new Center(
                BigDecimal.valueOf(lat + dLat).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(longitude.doubleValue() + dLng).setScale(6, RoundingMode.HALF_UP));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
