package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewFraudDetectionService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.BusinessConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A review's location badge follows the same CHECKIN.* settings as a check-in at the place. */
class ReviewServiceImplLocationTest {

    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final GeoService geoService = mock(GeoService.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final CheckinVerifier verifier = new CheckinVerifier(config, geoService, places);

    private final ReviewServiceImpl service = new ReviewServiceImpl(
            mock(StarService.class), mock(UserReviewRepository.class), mock(UserReviewProfileRepository.class),
            mock(PlaceScoreRepository.class), mock(ReviewHelpfulVoteRepository.class), mock(UserRepository.class),
            places, mock(UserCheckinRepository.class), mock(ActivityBookingRepository.class),
            mock(HotelMarketplaceRepository.class), mock(ActivityCommerceRepository.class),
            mock(MediaAssetRepository.class), mock(ReviewScoringService.class),
            mock(ReviewFraudDetectionService.class), mock(ImageStorageCleanupService.class),
            mock(SocialNotificationService.class), mock(ContentModerationService.class),
            mock(ContentCommentRepository.class), verifier);

    private final Place place = new Place();

    @BeforeEach
    void setUp() {
        place.setId(UUID.randomUUID());
        place.setLatitude(new BigDecimal("21.028500"));
        place.setLongitude(new BigDecimal("105.854200"));
        when(config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS)).thenReturn(100);
        when(config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS)).thenReturn(200);
        // No drawn area: the radius decides.
        when(places.isWithinVerificationGeometry(any(), any(), any(), any())).thenReturn(null);
    }

    @Test
    void verifiesAFixAtThePlace() {
        assertThat(service.isVerifiedLocation(request("21.028600", "105.854200", 20), place)).isTrue();
    }

    @Test
    void readsTheAccuracyLimitFromCheckinConfig() {
        when(config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS)).thenReturn(50);

        // 80 m passed the old hard-coded 100 m limit; the operator's 50 m now governs.
        assertThat(service.isVerifiedLocation(request("21.028600", "105.854200", 80), place)).isFalse();
    }

    @Test
    void readsTheRadiusFromCheckinConfig() {
        when(config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS)).thenReturn(500);

        // ~330 m north: outside the old hard-coded 200 m, inside the configured 500 m.
        assertThat(service.isVerifiedLocation(request("21.031500", "105.854200", 20), place)).isTrue();
    }

    @Test
    void usesThePlacesDrawnAreaWhenItHasOne() {
        when(places.isWithinVerificationGeometry(any(), any(), any(), any())).thenReturn(false);

        assertThat(service.isVerifiedLocation(request("21.028600", "105.854200", 20), place)).isFalse();
    }

    @Test
    void neverFallsBackToTheWard() {
        service.isVerifiedLocation(request("21.100000", "105.854200", 20), place);

        verify(geoService, never()).resolveWard(any(), any());
    }

    @Test
    void isNotVerifiedWithoutAnAccuracy() {
        CreateReviewRequest request = request("21.028600", "105.854200", 20);
        request.setCheckinAccuracy(null);

        assertThat(service.isVerifiedLocation(request, place)).isFalse();
    }

    private static CreateReviewRequest request(String lat, String lng, int accuracy) {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setCheckinLat(new BigDecimal(lat));
        request.setCheckinLng(new BigDecimal(lng));
        request.setCheckinAccuracy(BigDecimal.valueOf(accuracy));
        return request;
    }
}
