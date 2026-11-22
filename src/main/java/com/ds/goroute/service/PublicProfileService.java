package com.ds.goroute.service;

import com.ds.goroute.dto.response.PublicProfileCheckinResponse;
import com.ds.goroute.dto.response.PublicProfileTripResponse;
import com.ds.goroute.dto.response.PublicUserProfileResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A person's profile looked up by username, for the link they share
 * ({@code /u/{username}}). Open to guests: everything returned is what a stranger already
 * sees on the profile in the app. An unknown or deleted username is NOT_FOUND.
 */
public interface PublicProfileService {

    /** The profile header; {@code viewerId} only fills the follow flags and is null for guests. */
    PublicUserProfileResponse getProfile(String username, UUID viewerId);

    /** The profile as a guest sees it, or empty for an unknown or deleted username. */
    Optional<PublicUserProfileResponse> findGuestProfile(String username);

    /** Public, live, not taken down check-ins, newest visit first. */
    List<PublicProfileCheckinResponse> getCheckins(String username, int page, int size);

    /** Public, not deleted, not taken down trips, most recently shared first. */
    List<PublicProfileTripResponse> getTrips(String username, int page, int size);
}
