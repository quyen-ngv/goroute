package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinVerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stored check-in into a number of explorer points, and the reason for it (CHK-09).
 *
 * <p>Only signals the server can verify are used: which flow the photos came through, how
 * good the location fix was, and whether the location could be confirmed at all. Nothing
 * the client asserts enters the calculation.
 *
 * <p>The reason is produced alongside the number and stored with it, because "why did I
 * only get this much" is the first question anybody asks, and answering it after the fact
 * from a formula in somebody's head does not work.
 *
 * <p>Reasons are stored as codes rather than as an English sentence. The sentence was fine
 * while the only reader was a log line; it is not fine now that the author is shown the
 * reason the moment they earn it, in their own language.
 *
 * <p>A gallery photo earns less rather than being refused. Encouraging the more reliable
 * choice costs far less than blocking the many check-ins that are written after the moment
 * has passed -- somebody photographing their lunch and writing about it back at the hotel.
 */
@Component
@RequiredArgsConstructor
public class CheckinRewardCalculator {

    public static final String REASON_REWARDS_OFF = "REWARDS_OFF";
    public static final String REASON_PHOTO_CAMERA = "PHOTO_CAMERA";
    public static final String REASON_PHOTO_GALLERY = "PHOTO_GALLERY";
    public static final String REASON_LOCATION_UNVERIFIED = "LOCATION_UNVERIFIED";
    public static final String REASON_DAILY_CAP = "DAILY_CAP";

    /** Separator for the stored reason column, chosen because no code contains it. */
    public static final String REASON_SEPARATOR = ";";

    /** The outcome, with the explanation kept next to the number. */
    public record Reward(int points, List<String> reasonCodes) {

        public boolean isGranted() {
            return points > 0;
        }

        /** What goes in the {@code reward_reason} column, and back out to the client to localise. */
        public String reason() {
            return String.join(REASON_SEPARATOR, reasonCodes);
        }
    }

    private final BusinessConfigService config;

    public Reward calculate(UserCheckin checkin, int pointsAlreadyEarnedToday) {
        if (!config.getBoolean(BusinessConfigKey.CHECKIN_REWARD_ENABLED)) {
            return new Reward(0, List.of(REASON_REWARDS_OFF));
        }

        int base = config.getInt(BusinessConfigKey.CHECKIN_REWARD_BASE_POINTS);
        List<String> reasons = new ArrayList<>();
        double multiplier;

        if (checkin.getPhotoSource() != null && checkin.getPhotoSource().isLiveCapture()) {
            multiplier = config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_CAMERA_MULTIPLIER);
            reasons.add(REASON_PHOTO_CAMERA);
        } else {
            multiplier = config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_GALLERY_MULTIPLIER);
            reasons.add(REASON_PHOTO_GALLERY);
        }

        if (checkin.getVerificationStatus() != CheckinVerificationStatus.VERIFIED) {
            multiplier *= config.getDecimal(BusinessConfigKey.CHECKIN_REWARD_UNVERIFIED_MULTIPLIER);
            reasons.add(REASON_LOCATION_UNVERIFIED);
        }

        int points = (int) Math.floor(base * multiplier);

        // The daily ceiling exists from the first day rather than after the first abuse:
        // any point system attracts somebody who wants to farm it.
        int dailyCap = config.getInt(BusinessConfigKey.CHECKIN_REWARD_DAILY_CAP);
        int remaining = Math.max(0, dailyCap - pointsAlreadyEarnedToday);
        if (points > remaining) {
            points = remaining;
            reasons.add(REASON_DAILY_CAP);
        }

        return new Reward(points, List.copyOf(reasons));
    }

    /**
     * Splits a stored reason back into codes.
     *
     * <p>Rows written before this became a code list hold an English sentence; it comes back as a
     * single unrecognised entry rather than as garbage, and the client falls through to showing it
     * verbatim.
     */
    public static List<String> parseReasonCodes(String storedReason) {
        if (storedReason == null || storedReason.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(storedReason.split(REASON_SEPARATOR))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
