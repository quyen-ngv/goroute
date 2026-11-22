package com.ds.goroute.meetup.service;

import com.ds.goroute.type.MeetupLocationMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

/**
 * The point strangers see instead of the true one (adapted from {@code QuestSearchArea}). An AREA
 * meetup's circle is centred up to {@code 0.35 × radius} away from the host's point, so the true
 * point stays well inside the circle without being its centre; an EXACT meetup whose spot is not
 * revealed moves up to 150 m, inside the 300 m circle drawn for it.
 *
 * <p>The offset is random but seeded by the meetup id, the point, the mode and the radius: re-saving
 * an edit that did not move the point lands on the same displayed point.
 */
public final class MeetupDisplayPoint {

    static final double AREA_OFFSET_SHARE = 0.35;
    static final double EXACT_MAX_OFFSET_METERS = 150;
    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private MeetupDisplayPoint() {
    }

    public record Point(BigDecimal latitude, BigDecimal longitude) {
    }

    public static Point of(UUID meetupId, BigDecimal latitude, BigDecimal longitude,
                           MeetupLocationMode mode, Integer radiusM) {
        String seedText = meetupId + "|" + plain(latitude) + "|" + plain(longitude) + "|" + mode + "|" + radiusM;
        UUID seed = UUID.nameUUIDFromBytes(seedText.getBytes(StandardCharsets.UTF_8));
        Random random = new Random(seed.getMostSignificantBits() ^ seed.getLeastSignificantBits());

        double maxOffset = mode == MeetupLocationMode.AREA && radiusM != null
                ? AREA_OFFSET_SHARE * radiusM
                : EXACT_MAX_OFFSET_METERS;
        double bearing = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * maxOffset;

        double lat = latitude.doubleValue();
        double dLat = distance * Math.cos(bearing) / METERS_PER_DEGREE_LAT;
        double dLng = distance * Math.sin(bearing) / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
        return new Point(
                BigDecimal.valueOf(lat + dLat).setScale(7, RoundingMode.HALF_UP),
                BigDecimal.valueOf(longitude.doubleValue() + dLng).setScale(7, RoundingMode.HALF_UP));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
