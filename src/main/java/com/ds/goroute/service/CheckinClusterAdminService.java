package com.ds.goroute.service;

import com.ds.goroute.dto.request.PromoteCheckinClusterRequest;
import com.ds.goroute.dto.response.CheckinClusterResponse;

import java.util.List;
import java.util.UUID;

/**
 * Turning places people actually go to into catalogue entries (CHK-12).
 *
 * <p>This is additive curation, not damage control. Because users never write into the
 * catalogue, nobody is chasing bad rows: an operator picks what is worth adding from a
 * list that real use has already filtered. Nothing breaks if it goes unattended for a
 * while -- the catalogue simply grows more slowly, which is why this is not a launch gate.
 */
public interface CheckinClusterAdminService {

    /** Clusters worth looking at, most distinct visitors first. */
    List<CheckinClusterResponse> queue(int page, int size);

    long countQueue();

    CheckinClusterResponse get(String locationKey);

    /**
     * Creates a catalogue entry from the cluster, attaches every check-in in it, and turns
     * the ratings people already gave into real reviews.
     *
     * <p>Where somebody rated the same spot more than once, their most recent score is the
     * one that becomes their review: a review is one opinion per person, and the newest is
     * the one they still hold. The older scores stay on their own check-ins as part of
     * those memories, they simply do not count towards the average.
     */
    CheckinClusterResponse promote(UUID operatorId, String locationKey, PromoteCheckinClusterRequest request);

    /** Recognises the cluster as somewhere already catalogued under a different name. */
    CheckinClusterResponse merge(UUID operatorId, String locationKey, UUID placeId);

    /** Not worth cataloguing. Returns to the queue if the cluster grows substantially. */
    void ignore(UUID operatorId, String locationKey, String note);

    /** Undoes a promotion, for the case where it was simply a mistake. */
    void revertPromotion(UUID operatorId, String locationKey);
}
