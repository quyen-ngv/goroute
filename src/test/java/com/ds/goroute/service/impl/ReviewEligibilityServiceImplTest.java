package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateReviewRequest;
import com.ds.goroute.dto.response.ReviewEligibilityResponse;
import com.ds.goroute.dto.response.ReviewEligibilityResponse.Reason;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.entity.ActivityOrder;
import com.ds.goroute.entity.HotelBooking;
import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewFraudDetectionService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.notification.SocialNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewEligibilityServiceImplTest {

    private final UserReviewRepository reviews = mock(UserReviewRepository.class);
    private final UserReviewProfileRepository profiles = mock(UserReviewProfileRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
    private final ActivityCommerceRepository activities = mock(ActivityCommerceRepository.class);
    private final MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
    private final ReviewScoringService scoring = mock(ReviewScoringService.class);

    private final ReviewServiceImpl service = new ReviewServiceImpl(
            mock(StarService.class), reviews, profiles, mock(PlaceScoreRepository.class),
            mock(ReviewHelpfulVoteRepository.class), mock(UserRepository.class), places,
            mock(UserCheckinRepository.class), mock(ActivityBookingRepository.class), hotels, activities,
            mediaAssets, scoring, mock(ReviewFraudDetectionService.class), mock(ImageStorageCleanupService.class),
            mock(SocialNotificationService.class));

    private final UUID user = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();

    private void hotelBooking(UUID owner, String status) {
        when(hotels.findBooking(bookingId)).thenReturn(Optional.of(
                HotelBooking.builder().id(bookingId).userId(owner).hotelId(hotelId).bookingStatus(status).build()));
        when(hotels.findHotel(hotelId)).thenReturn(Optional.of(HotelProfile.builder().id(hotelId).placeId(placeId).build()));
    }

    private void activityOrder(UUID owner, String status) {
        when(activities.findOrder(orderId)).thenReturn(Optional.of(
                ActivityOrder.builder().id(orderId).userId(owner).activityBookingId(activityId).orderStatus(status).build()));
    }

    @Test
    void unknownBookingIsNotFound() {
        when(hotels.findBooking(bookingId)).thenReturn(Optional.empty());
        ReviewEligibilityResponse r = service.getEligibility(user, bookingId, null);
        assertFalse(r.isEligible());
        assertEquals(Reason.NOT_FOUND, r.getReason());
        assertNull(r.getPlaceId());
    }

    @Test
    void someoneElsesBookingIsNotOwnerAndLeaksNoTarget() {
        hotelBooking(UUID.randomUUID(), "COMPLETED");
        ReviewEligibilityResponse r = service.getEligibility(user, bookingId, null);
        assertEquals(Reason.NOT_OWNER, r.getReason());
        assertNull(r.getPlaceId());
        assertNull(r.getExistingReviewId());
    }

    @Test
    void pendingOrConfirmedBookingIsNotCompleted() {
        hotelBooking(user, "CONFIRMED");
        assertEquals(Reason.NOT_COMPLETED, service.getEligibility(user, bookingId, null).getReason());
        hotelBooking(user, "CANCELLED_BY_GUEST");
        assertEquals(Reason.NOT_COMPLETED, service.getEligibility(user, bookingId, null).getReason());
    }

    @Test
    void completedOrCheckedInStayIsEligibleWithDerivedPlace() {
        hotelBooking(user, "COMPLETED");
        ReviewEligibilityResponse r = service.getEligibility(user, bookingId, null);
        assertTrue(r.isEligible());
        assertEquals(Reason.OK, r.getReason());
        assertEquals(placeId, r.getPlaceId());
        assertNull(r.getActivityBookingId());

        hotelBooking(user, "CHECKED_IN");
        assertTrue(service.getEligibility(user, bookingId, null).isEligible());
    }

    @Test
    void bookingAlreadyReviewedReportsExistingReview() {
        hotelBooking(user, "COMPLETED");
        UUID existing = UUID.randomUUID();
        when(reviews.findByHotelBookingId(bookingId)).thenReturn(Optional.of(UserReview.builder().id(existing).build()));
        ReviewEligibilityResponse r = service.getEligibility(user, bookingId, null);
        assertFalse(r.isEligible());
        assertEquals(Reason.ALREADY_REVIEWED, r.getReason());
        assertEquals(existing, r.getExistingReviewId());
        assertEquals(placeId, r.getPlaceId());
    }

    @Test
    void earlierPlaceReviewBlocksRepeatStayBecauseOfPerPlaceUniqueness() {
        hotelBooking(user, "COMPLETED");
        UUID existing = UUID.randomUUID();
        when(reviews.findByUserAndPlace(user, placeId)).thenReturn(Optional.of(UserReview.builder().id(existing).build()));
        ReviewEligibilityResponse r = service.getEligibility(user, bookingId, null);
        assertEquals(Reason.ALREADY_REVIEWED, r.getReason());
        assertEquals(existing, r.getExistingReviewId());
    }

    @Test
    void activityOrderDerivesActivityBookingId() {
        activityOrder(user, "CHECKED_IN");
        ReviewEligibilityResponse r = service.getEligibility(user, null, orderId);
        assertTrue(r.isEligible());
        assertEquals(activityId, r.getActivityBookingId());
        assertNull(r.getPlaceId());

        activityOrder(user, "NO_SHOW");
        assertEquals(Reason.NOT_COMPLETED, service.getEligibility(user, null, orderId).getReason());
        activityOrder(UUID.randomUUID(), "COMPLETED");
        assertEquals(Reason.NOT_OWNER, service.getEligibility(user, null, orderId).getReason());
    }

    @Test
    void selectorMustNameExactlyOneBooking() {
        assertEquals(ErrorConstant.INVALID_PARAMETERS,
                assertThrows(BusinessException.class, () -> service.getEligibility(user, null, null)).getError().getCode());
        assertEquals(ErrorConstant.INVALID_PARAMETERS,
                assertThrows(BusinessException.class, () -> service.getEligibility(user, bookingId, orderId)).getError().getCode());
    }

    @Test
    void createReviewRefusesNonOwnerWith403AndUnfinishedStayWith400() {
        hotelBooking(UUID.randomUUID(), "COMPLETED");
        BusinessException forbidden = assertThrows(BusinessException.class, () -> service.createReview(user, request(bookingId, null)));
        assertEquals(ErrorConstant.FORBIDDEN_ERROR, forbidden.getError().getCode());

        hotelBooking(user, "PENDING_PARTNER_CONFIRMATION");
        BusinessException early = assertThrows(BusinessException.class, () -> service.createReview(user, request(bookingId, null)));
        assertEquals(ErrorConstant.BAD_REQUEST, early.getError().getCode());
        assertEquals("You can review after your stay/visit", early.getMessage());

        hotelBooking(user, "COMPLETED");
        when(reviews.findByHotelBookingId(bookingId)).thenReturn(Optional.of(UserReview.builder().id(UUID.randomUUID()).build()));
        BusinessException dup = assertThrows(BusinessException.class, () -> service.createReview(user, request(bookingId, null)));
        assertEquals(ErrorConstant.REVIEW_ALREADY_EXISTS, dup.getError().getCode());
        verify(reviews, never()).save(any());
    }

    @Test
    void createReviewFromCompletedStayDerivesPlaceAndStoresBookingId() {
        hotelBooking(user, "COMPLETED");
        when(places.findById(placeId)).thenReturn(Optional.of(Place.builder().id(placeId).title("Sea View").build()));
        CreateReviewRequest request = request(bookingId, null);
        request.setPlaceId(UUID.randomUUID()); // client-supplied target is ignored in favour of the booking's hotel

        UserReviewResponse response = service.createReview(user, request);

        ArgumentCaptor<UserReview> saved = ArgumentCaptor.forClass(UserReview.class);
        verify(reviews).save(saved.capture());
        assertEquals(placeId, saved.getValue().getPlaceId());
        assertEquals(bookingId, saved.getValue().getHotelBookingId());
        assertNull(saved.getValue().getActivityOrderId());
        assertNull(saved.getValue().getActivityBookingId());
        assertTrue(response.getVerifiedStay());
        assertEquals(bookingId, response.getHotelBookingId());
        assertEquals(placeId, response.getPlaceId());
        assertNull(response.getPartnerResponse());
        verify(scoring).recalculatePlaceScores(placeId);
    }

    @Test
    void createReviewFromActivityOrderStoresOrderId() {
        activityOrder(user, "COMPLETED");
        ActivityBookingRepository activityBookings = mock(ActivityBookingRepository.class);
        when(activityBookings.findById(activityId)).thenReturn(Optional.of(new com.ds.goroute.entity.ActivityBooking()));
        ReviewServiceImpl withBookings = new ReviewServiceImpl(
                mock(StarService.class), reviews, profiles, mock(PlaceScoreRepository.class),
                mock(ReviewHelpfulVoteRepository.class), mock(UserRepository.class), places,
                mock(UserCheckinRepository.class), activityBookings, hotels, activities,
                mediaAssets, scoring, mock(ReviewFraudDetectionService.class), mock(ImageStorageCleanupService.class),
                mock(SocialNotificationService.class));

        UserReviewResponse response = withBookings.createReview(user, request(null, orderId));

        ArgumentCaptor<UserReview> saved = ArgumentCaptor.forClass(UserReview.class);
        verify(reviews).save(saved.capture());
        assertEquals(activityId, saved.getValue().getActivityBookingId());
        assertEquals(orderId, saved.getValue().getActivityOrderId());
        assertNull(saved.getValue().getPlaceId());
        assertTrue(response.getVerifiedStay());
        assertEquals(orderId, response.getActivityOrderId());
    }

    @Test
    void plainPlaceReviewIsNotAVerifiedStay() {
        when(places.findById(placeId)).thenReturn(Optional.of(Place.builder().id(placeId).build()));
        CreateReviewRequest request = CreateReviewRequest.builder().placeId(placeId).overallRating(5).build();

        UserReviewResponse response = service.createReview(user, request);

        assertFalse(response.getVerifiedStay());
        assertNull(response.getHotelBookingId());
        verify(hotels, never()).findBooking(any());
    }

    private static CreateReviewRequest request(UUID hotelBookingId, UUID activityOrderId) {
        return CreateReviewRequest.builder()
                .hotelBookingId(hotelBookingId)
                .activityOrderId(activityOrderId)
                .overallRating(4)
                .text("Lovely stay")
                .build();
    }
}
