package com.ds.goroute.event;

import java.util.UUID;

/**
 * Published when a trip turns PUBLIC. Followers of the owner are told after commit, once per trip
 * ever: the listener claims {@code trips.followers_notified_at}, so turning it private and public
 * again announces nothing.
 */
public record TripSharedPublicEvent(UUID tripId, UUID ownerId) {
}
