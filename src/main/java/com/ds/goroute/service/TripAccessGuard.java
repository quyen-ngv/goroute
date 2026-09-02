package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * The one answer to "may this user act inside this trip?".
 *
 * <p>Activities, check-ins and expenses each carried their own private copy of this
 * check, under three different names. They agreed today; three copies is three places for
 * them to stop agreeing.
 *
 * <p>There are two questions, not one. Reading a trip needs membership; changing it needs a role
 * that is allowed to change it. Collapsing them is what made VIEWER decorative: the role column
 * existed, the invite screen offered it, and an accepted viewer could still delete the whole
 * itinerary. Callers pick the question they mean, and the answer is the same everywhere.
 */
@Service
@RequiredArgsConstructor
public class TripAccessGuard {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;

    /**
     * @return the trip, so a caller that needs it does not have to load it a second time
     * @throws BusinessException 404 when the trip is gone, 403 when the user is neither
     *                           its owner nor an accepted member
     */
    public Trip requireAccess(UUID tripId, UUID userId) {
        Trip trip = requireTrip(tripId);
        if (!hasAccess(trip, userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }
        return trip;
    }

    /**
     * Same, for anything that writes: the owner, or an accepted member whose role is EDITOR.
     *
     * <p>The refusal says which role the user holds, because "access denied" on a trip they can
     * plainly see reads as a bug rather than as the role they were invited under.
     */
    public Trip requireEditAccess(UUID tripId, UUID userId) {
        Trip trip = requireTrip(tripId);
        if (trip.getOwnerId().equals(userId)) {
            return trip;
        }
        Optional<TripMember> member = acceptedMember(trip, userId);
        if (member.isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }
        if (!canEdit(member.get().getRole())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "You joined this trip as a viewer and cannot change it. "
                            + "Ask the trip owner to make you an editor.");
        }
        return trip;
    }

    /** Whether this user may change the trip, for callers that answer rather than refuse. */
    public boolean canEdit(Trip trip, UUID userId) {
        return trip.getOwnerId().equals(userId)
                || acceptedMember(trip, userId).map(TripMember::getRole).filter(this::canEdit).isPresent();
    }

    private boolean canEdit(MemberRole role) {
        // A null role is old data written before the column was populated. Those rows were
        // collaborators in every other respect, so they keep editing rather than losing it on
        // deploy day; only an explicit VIEWER is held back.
        return role != MemberRole.VIEWER;
    }

    private Trip requireTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
    }

    private boolean hasAccess(Trip trip, UUID userId) {
        return trip.getOwnerId().equals(userId) || acceptedMember(trip, userId).isPresent();
    }

    private Optional<TripMember> acceptedMember(Trip trip, UUID userId) {
        return tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId)
                .filter(member -> MemberStatus.ACCEPTED.equals(member.getStatus()));
    }
}
