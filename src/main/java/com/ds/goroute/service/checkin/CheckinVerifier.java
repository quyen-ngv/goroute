package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinVerificationScope;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.utils.GeoDistance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Decides whether a check-in's location is proven, and at which level.
 *
 * <p>One component rather than a private method so the two writers -- the author
 * creating a check-in and an operator attaching a place to one later -- reach the same
 * verdict from the same rules. Only signals the server can check are used: whether the
 * photo came through the in-app camera, how good the GPS fix was, and where the point
 * falls relative to the place and to the administrative boundaries.
 *
 * <pre>
 * gallery photo, or no usable accuracy          → UNVERIFIED / NONE
 * place has a drawn area: inside it, or within the fix's own error of it
 * place has no area:      within the effective radius
 *                                                → VERIFIED / PLACE
 * otherwise, point inside some ward              → VERIFIED / WARD
 * otherwise                                      → UNVERIFIED / NONE
 * </pre>
 *
 * <p>The ladder deliberately stops at the ward: a province is too big to be evidence.
 * A gallery photo never climbs it at all -- nobody knows where or when it was taken.
 */
@Component
@RequiredArgsConstructor
public class CheckinVerifier {

    private final BusinessConfigService config;
    private final GeoService geoService;
    private final PlaceRepository placeRepository;

    /** Everything a preview or a verdict needs, computed once from the coordinates. */
    public record Assessment(
            Double distanceMeters,
            /** Null when there is no place; otherwise whether the point counts as at the place. */
            Boolean withinPlaceArea,
            boolean placeHasGeometry,
            int effectiveRadiusMeters,
            Boolean accuracyAcceptable,
            Optional<Ward> ward) {

        /** The scope a live-capture check-in at this point would receive. */
        public CheckinVerificationScope scopeIfLiveCapture() {
            if (!Boolean.TRUE.equals(accuracyAcceptable)) {
                return CheckinVerificationScope.NONE;
            }
            if (Boolean.TRUE.equals(withinPlaceArea)) {
                return CheckinVerificationScope.PLACE;
            }
            return ward.isPresent() ? CheckinVerificationScope.WARD : CheckinVerificationScope.NONE;
        }
    }

    public Assessment assess(Place place, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        int maxAccuracy = config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS);
        Boolean accuracyAcceptable = accuracyMeters == null ? null : accuracyMeters.doubleValue() <= maxAccuracy;
        int radius = effectiveRadius(place);
        Double distance = place == null ? null
                : GeoDistance.betweenOrNull(latitude, longitude, place.getLatitude(), place.getLongitude());

        Boolean withinPlace = null;
        boolean hasGeometry = false;
        if (place != null && latitude != null && longitude != null) {
            // The fix's own error is allowed as slack against a drawn edge: standing on the
            // lakeside path with a 15 m fix should not fail a polygon traced along the water.
            Boolean insideGeometry = placeRepository.isWithinVerificationGeometry(
                    place.getId(), latitude, longitude, accuracyMeters == null ? BigDecimal.ZERO : accuracyMeters);
            if (insideGeometry != null) {
                hasGeometry = true;
                withinPlace = insideGeometry;
            } else {
                withinPlace = distance != null && distance <= radius;
            }
        }
        return new Assessment(distance, withinPlace, hasGeometry, radius, accuracyAcceptable,
                geoService.resolveWard(latitude, longitude));
    }

    /** Writes the verdict, the distance and the resolved ward onto the check-in. */
    public void apply(UserCheckin checkin, Place place) {
        Assessment assessment = assess(place, checkin.getLatitude(), checkin.getLongitude(), checkin.getAccuracyMeters());
        if (assessment.distanceMeters() != null) {
            checkin.setDistanceMeters(BigDecimal.valueOf(assessment.distanceMeters()));
        }
        // The ward is recorded whatever the verdict: the map on the profile and the
        // passport want to know where a gallery photo was posted from, they just must
        // not call it proven.
        assessment.ward().ifPresentOrElse(ward -> {
            checkin.setWardCode(ward.getCode());
            checkin.setProvinceCode(ward.getProvinceCode());
            checkin.setWard(ward.getFullName());
            checkin.setProvince(ward.getProvinceName());
            // The district tier was abolished in 2025 and the dataset has no such level,
            // so a reverse-geocoded "Quận Hoàn Kiếm" beside the official "Phường Hoàn
            // Kiếm" would print an address that no longer exists. Official names win
            // whole, not field by field.
            checkin.setDistrict(null);
        }, () -> checkin.setWardCode(null));

        boolean liveCapture = checkin.getPhotoSource() != null && checkin.getPhotoSource().isLiveCapture();
        CheckinVerificationScope scope = liveCapture
                ? assessment.scopeIfLiveCapture()
                : CheckinVerificationScope.NONE;
        checkin.setVerificationScope(scope);
        checkin.setVerificationStatus(scope == CheckinVerificationScope.NONE
                ? CheckinVerificationStatus.UNVERIFIED
                : CheckinVerificationStatus.VERIFIED);
    }

    /** The place's own radius when it is set and sane; otherwise the global default. */
    public int effectiveRadius(Place place) {
        int global = config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS);
        Integer configured = place == null ? null : place.getVerificationRadiusMeters();
        return configured != null && configured >= 20 ? configured : global;
    }
}
