package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The one answer to "may this user act inside this trip?".
 *
 * <p>Activities, check-ins and expenses each carried their own private copy of this
 * check, under three different names. They agreed today; three copies is three places for
 * them to stop agreeing.
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
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        if (!hasAccess(trip, userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Access denied");
        }
        return trip;
    }

    private boolean hasAccess(Trip trip, UUID userId) {
        if (trip.getOwnerId().equals(userId)) {
            return true;
        }
        return tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId)
                .map(TripMember::getStatus)
                .filter(MemberStatus.ACCEPTED::equals)
                .isPresent();
    }
}
