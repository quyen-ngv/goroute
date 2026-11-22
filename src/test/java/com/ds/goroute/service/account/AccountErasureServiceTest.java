package com.ds.goroute.service.account;

import com.ds.goroute.repository.AccountErasureRepository;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountErasureServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private AccountErasureRepository repository;
    @Mock private UserCheckinService checkins;
    @Mock private ReviewService reviews;
    @Mock private TripService trips;

    private AccountErasureService service;

    @BeforeEach
    void setUp() {
        service = new AccountErasureService(repository, checkins, reviews, trips);
    }

    @Test
    void contentGoesThroughItsOwnersBeforeTheRowIsAnonymised() {
        UUID review = UUID.randomUUID();
        UUID checkin = UUID.randomUUID();
        UUID trip = UUID.randomUUID();
        when(repository.findReviewIds(USER)).thenReturn(List.of(review));
        when(repository.findLiveCheckinIds(USER)).thenReturn(List.of(checkin));
        when(repository.findSoloOwnedTripIds(USER)).thenReturn(List.of(trip));

        service.erase(USER);

        InOrder order = inOrder(reviews, checkins, trips, repository);
        order.verify(reviews).deleteReview(USER, review);
        order.verify(checkins).removeAsAdmin(checkin, false);
        order.verify(trips).deleteTrip(trip, USER);
        order.verify(repository).removeComments(USER);
        order.verify(repository).withdrawFromMeetups(USER);
        order.verify(repository).deleteAssistantData(USER);
        order.verify(repository).clearAiCallPayloads(USER);
        order.verify(repository).deletePrivateRecords(USER);
        order.verify(repository).anonymiseUser(eq(USER), anyString(), anyString());
    }

    @Test
    void theAnonymisedEmailIsUniquePerAccountAndCannotReceiveMail() {
        when(repository.findReviewIds(USER)).thenReturn(List.of());
        when(repository.findLiveCheckinIds(USER)).thenReturn(List.of());
        when(repository.findSoloOwnedTripIds(USER)).thenReturn(List.of());

        service.erase(USER);

        verify(repository).anonymiseUser(USER, "deleted+" + USER + "@deleted.ondetour.invalid", "Deleted user");
    }

    @Test
    void unerasedAccountsAreLookedUpByTheAnonymousDomain() {
        service.findUnerased(100);

        verify(repository).findUnerasedDeletedUserIds("@deleted.ondetour.invalid", 100);
    }

}
