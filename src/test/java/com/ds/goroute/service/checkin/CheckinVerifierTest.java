package com.ds.goroute.service.checkin;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinPhotoSource;
import com.ds.goroute.type.CheckinVerificationScope;
import com.ds.goroute.type.CheckinVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The decision table of {@link CheckinVerifier}. Every row is one branch of the ladder:
 * gallery, bad fix, at the place (radius or drawn area), in a ward only, nowhere.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CheckinVerifier")
class CheckinVerifierTest {

    // Hồ Hoàn Kiếm and a point ~120 m north of it, both inside P. Hoàn Kiếm.
    private static final BigDecimal LAKE_LAT = new BigDecimal("21.0288");
    private static final BigDecimal LAKE_LNG = new BigDecimal("105.8524");
    private static final BigDecimal NEAR_LAT = new BigDecimal("21.0299");
    private static final BigDecimal FAR_LAT = new BigDecimal("21.0400");

    @Mock private BusinessConfigService config;
    @Mock private GeoService geoService;
    @Mock private PlaceRepository placeRepository;

    private CheckinVerifier verifier;
    private Place lake;
    private Ward hoanKiem;

    @BeforeEach
    void setUp() {
        verifier = new CheckinVerifier(config, geoService, placeRepository);
        when(config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS)).thenReturn(200);
        when(config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS)).thenReturn(100);
        lake = Place.builder().id(UUID.randomUUID()).latitude(LAKE_LAT).longitude(LAKE_LNG).build();
        hoanKiem = Ward.builder().code("00070").provinceCode("01").fullName("Phường Hoàn Kiếm")
                .provinceName("Hà Nội").build();
        when(geoService.resolveWard(any(), any())).thenReturn(Optional.of(hoanKiem));
        when(placeRepository.isWithinVerificationGeometry(any(), any(), any(), any())).thenReturn(null);
    }

    @Test
    @DisplayName("camera photo inside the radius is verified at the place")
    void cameraInsideRadiusIsPlace() {
        UserCheckin checkin = camera(NEAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationStatus()).isEqualTo(CheckinVerificationStatus.VERIFIED);
        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.PLACE);
        assertThat(checkin.getDistanceMeters()).isNotNull();
        assertThat(checkin.getWardCode()).isEqualTo("00070");
        assertThat(checkin.getProvinceCode()).isEqualTo("01");
        assertThat(checkin.getWard()).isEqualTo("Phường Hoàn Kiếm");
    }

    @Test
    @DisplayName("camera photo outside the radius but inside a ward escalates to the ward")
    void cameraOutsideRadiusInsideWardIsWard() {
        UserCheckin checkin = camera(FAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationStatus()).isEqualTo(CheckinVerificationStatus.VERIFIED);
        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.WARD);
    }

    @Test
    @DisplayName("camera photo with no place at all is verified at the ward")
    void cameraWithoutPlaceIsWard() {
        UserCheckin checkin = camera(NEAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, null);

        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.WARD);
        assertThat(checkin.getDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("the ladder never climbs to a province: outside every ward is unverified")
    void outsideEveryWardIsUnverified() {
        when(geoService.resolveWard(any(), any())).thenReturn(Optional.empty());
        UserCheckin checkin = camera(FAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationStatus()).isEqualTo(CheckinVerificationStatus.UNVERIFIED);
        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.NONE);
        assertThat(checkin.getWardCode()).isNull();
    }

    @Test
    @DisplayName("a gallery photo records the ward but is never verified")
    void galleryRecordsWardButStaysUnverified() {
        UserCheckin checkin = camera(NEAR_LAT, LAKE_LNG, "5");
        checkin.setPhotoSource(CheckinPhotoSource.GALLERY);

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationStatus()).isEqualTo(CheckinVerificationStatus.UNVERIFIED);
        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.NONE);
        assertThat(checkin.getWardCode()).isEqualTo("00070");
    }

    @Test
    @DisplayName("a fix worse than the ceiling, or no fix, is unverified even at the place")
    void badAccuracyIsUnverified() {
        UserCheckin tooCoarse = camera(NEAR_LAT, LAKE_LNG, "250");
        UserCheckin missing = camera(NEAR_LAT, LAKE_LNG, null);

        verifier.apply(tooCoarse, lake);
        verifier.apply(missing, lake);

        assertThat(tooCoarse.getVerificationScope()).isEqualTo(CheckinVerificationScope.NONE);
        assertThat(missing.getVerificationScope()).isEqualTo(CheckinVerificationScope.NONE);
    }

    @Test
    @DisplayName("a drawn area replaces the radius: outside the polygon fails even when close")
    void drawnAreaReplacesRadius() {
        when(placeRepository.isWithinVerificationGeometry(eq(lake.getId()), any(), any(), any())).thenReturn(false);
        UserCheckin checkin = camera(NEAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.WARD);
    }

    @Test
    @DisplayName("a drawn area replaces the radius: inside the polygon passes even when far from the pin")
    void drawnAreaPassesFarFromPin() {
        when(placeRepository.isWithinVerificationGeometry(eq(lake.getId()), any(), any(), any())).thenReturn(true);
        UserCheckin checkin = camera(FAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.PLACE);
    }

    @Test
    @DisplayName("the place's own radius wins over the global one when it is at least 20 m")
    void placeRadiusOverridesGlobal() {
        lake.setVerificationRadiusMeters(2000);
        UserCheckin checkin = camera(FAR_LAT, LAKE_LNG, "15");

        verifier.apply(checkin, lake);

        assertThat(checkin.getVerificationScope()).isEqualTo(CheckinVerificationScope.PLACE);
        assertThat(verifier.effectiveRadius(lake)).isEqualTo(2000);
        lake.setVerificationRadiusMeters(5);
        assertThat(verifier.effectiveRadius(lake)).isEqualTo(200);
    }

    @Test
    @DisplayName("the preview reports the same scope the verdict will use")
    void previewMatchesVerdict() {
        CheckinVerifier.Assessment assessment = verifier.assess(lake, FAR_LAT, LAKE_LNG, new BigDecimal("15"));

        assertThat(assessment.withinPlaceArea()).isFalse();
        assertThat(assessment.placeHasGeometry()).isFalse();
        assertThat(assessment.ward()).contains(hoanKiem);
        assertThat(assessment.scopeIfLiveCapture()).isEqualTo(CheckinVerificationScope.WARD);
        assertThat(verifier.assess(lake, FAR_LAT, LAKE_LNG, null).scopeIfLiveCapture())
                .isEqualTo(CheckinVerificationScope.NONE);
    }

    @Test
    @DisplayName("a map-pinned target inside the radius reaches PLACE without a places row")
    void mapPinnedTargetInsideRadiusIsPlace() {
        VerificationTarget target = VerificationTarget.forCoordinates(LAKE_LAT, LAKE_LNG, 40);

        // Standing on the pin: distance ~0, well inside the 40 m radius.
        CheckinVerifier.Assessment assessment = verifier.assess(target, LAKE_LAT, LAKE_LNG, new BigDecimal("15"));

        assertThat(assessment.withinPlaceArea()).isTrue();
        assertThat(assessment.placeHasGeometry()).isFalse();
        assertThat(assessment.effectiveRadiusMeters()).isEqualTo(40);
        assertThat(assessment.scopeIfLiveCapture()).isEqualTo(CheckinVerificationScope.PLACE);
    }

    @Test
    @DisplayName("a map-pinned target that misses the radius is NONE, never WARD (ward fallback off)")
    void mapPinnedTargetOutsideRadiusNeverEscalatesToWard() {
        VerificationTarget target = VerificationTarget.forCoordinates(LAKE_LAT, LAKE_LNG, 40);

        CheckinVerifier.Assessment assessment = verifier.assess(target, FAR_LAT, LAKE_LNG, new BigDecimal("15"));

        assertThat(assessment.withinPlaceArea()).isFalse();
        assertThat(assessment.ward()).isEmpty();
        assertThat(assessment.scopeIfLiveCapture()).isEqualTo(CheckinVerificationScope.NONE);
    }

    @Test
    @DisplayName("a map-pinned target uses its own radius, not the place radius or check-in config")
    void mapPinnedTargetUsesOwnRadius() {
        // 40 m would miss FAR_LAT (~1240 m north); a 2000 m target radius reaches it.
        assertThat(verifier.assess(VerificationTarget.forCoordinates(LAKE_LAT, LAKE_LNG, 40),
                FAR_LAT, LAKE_LNG, new BigDecimal("15")).scopeIfLiveCapture())
                .isEqualTo(CheckinVerificationScope.NONE);
        assertThat(verifier.assess(VerificationTarget.forCoordinates(LAKE_LAT, LAKE_LNG, 2000),
                FAR_LAT, LAKE_LNG, new BigDecimal("15")).scopeIfLiveCapture())
                .isEqualTo(CheckinVerificationScope.PLACE);
    }

    private static UserCheckin camera(BigDecimal latitude, BigDecimal longitude, String accuracy) {
        return UserCheckin.builder()
                .id(UUID.randomUUID())
                .latitude(latitude)
                .longitude(longitude)
                .accuracyMeters(accuracy == null ? null : new BigDecimal(accuracy))
                .photoSource(CheckinPhotoSource.CAMERA)
                .build();
    }
}
