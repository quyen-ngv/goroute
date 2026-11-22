package com.ds.goroute.repository;

import java.util.UUID;

/** {@code place_contributors}: who is credited on a place's "Contributed by" list. */
public interface PlaceContributorRepository {

    /** Credits the user on the place; crediting twice is a no-op (primary key place_id, user_id). */
    void credit(UUID placeId, UUID userId);
}
