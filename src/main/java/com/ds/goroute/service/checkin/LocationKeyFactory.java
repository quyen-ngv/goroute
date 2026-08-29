package com.ds.goroute.service.checkin;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns a coordinate into the key that decides whether two check-ins happened at the same
 * spot (CHK-04).
 *
 * <p>This is the riskiest knob in the epic. Round too coarsely and two cafés next door to
 * each other merge into one; round too finely and one café splits into a different place
 * for every table. It is configurable for exactly that reason, and the distribution of
 * cluster sizes is worth watching from the first week.
 *
 * <p>When the check-in is attached to a catalogued place, the key is derived from the
 * <em>place's</em> coordinates rather than the phone's. Otherwise everyone standing at the
 * back door of the same restaurant would form a second cluster from the people at the
 * front door.
 */
@Component
@RequiredArgsConstructor
public class LocationKeyFactory {

    private final BusinessConfigService config;

    public String forCoordinates(BigDecimal latitude, BigDecimal longitude) {
        int precision = config.getInt(BusinessConfigKey.CHECKIN_LOCATION_KEY_PRECISION);
        return "g" + precision + ":" + round(latitude, precision) + "," + round(longitude, precision);
    }

    /** A catalogued place is its own cluster, and stays one even if its coordinates move. */
    public String forPlace(java.util.UUID placeId) {
        return "p:" + placeId;
    }

    private String round(BigDecimal value, int precision) {
        return value.setScale(precision, RoundingMode.HALF_UP).toPlainString();
    }
}
