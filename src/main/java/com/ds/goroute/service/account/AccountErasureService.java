package com.ds.goroute.service.account;

import com.ds.goroute.repository.AccountErasureRepository;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Erases a deleted account (Privacy Policy 2.0 §10.2; Luật 91/2025/QH15 right to deletion).
 *
 * <p>The users row is kept, anonymised: many tables point at it (some with RESTRICT foreign keys)
 * and what other people still use — trip chat messages, shared-trip activities and expenses,
 * partner bookings, credit ledgers, moderation records — stays readable as "Deleted user". The
 * email and sign-in provider id are replaced, so signing up or signing in with Google/Apple
 * again creates a new, empty account instead of restoring this one.
 *
 * <p>The person's own content goes through the services that own it, so place scores, counters
 * and stored files follow: check-ins are removed, reviews deleted, and trips nobody else joined
 * deleted. Trips other accounts have joined stay with them; their owner shows as "Deleted user".
 * Private records (follows, blocks, saved items, feed signals, notifications, Pengoo's notes and
 * threads, AI prompts) are deleted outright.
 *
 * <p>Runs in one transaction: a failure leaves the account as it was, never half erased.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    /** The domain of every anonymised email; reserved (.invalid), so it can never receive mail. */
    public static final String ANONYMOUS_EMAIL_SUFFIX = "@deleted.ondetour.invalid";
    static final String ANONYMOUS_NAME = "Deleted user";

    private final AccountErasureRepository repository;
    private final UserCheckinService checkinService;
    private final ReviewService reviewService;
    private final TripService tripService;

    @Transactional
    public void erase(UUID userId) {
        // Reviews before check-ins: a check-in removal leaves its review in place, and deleting the
        // review first detaches it and recalculates the place's score.
        List<UUID> reviewIds = repository.findReviewIds(userId);
        for (UUID reviewId : reviewIds) {
            reviewService.deleteReview(userId, reviewId);
        }
        List<UUID> checkinIds = repository.findLiveCheckinIds(userId);
        for (UUID checkinId : checkinIds) {
            checkinService.removeAsAdmin(checkinId, false);
        }
        List<UUID> tripIds = repository.findSoloOwnedTripIds(userId);
        for (UUID tripId : tripIds) {
            tripService.deleteTrip(tripId, userId);
        }

        repository.removeComments(userId);
        repository.withdrawFromMeetups(userId);
        repository.deleteAssistantData(userId);
        repository.clearAiCallPayloads(userId);
        repository.deletePrivateRecords(userId);
        repository.anonymiseUser(userId, "deleted+" + userId + ANONYMOUS_EMAIL_SUFFIX, ANONYMOUS_NAME);

        log.info("Account erased: {} ({} reviews, {} check-ins, {} trips)",
                userId, reviewIds.size(), checkinIds.size(), tripIds.size());
    }

    /** Accounts deleted before erasure existed, still holding personal data; oldest first. */
    @Transactional(readOnly = true)
    public List<UUID> findUnerased(int limit) {
        return repository.findUnerasedDeletedUserIds(ANONYMOUS_EMAIL_SUFFIX, limit);
    }
}
