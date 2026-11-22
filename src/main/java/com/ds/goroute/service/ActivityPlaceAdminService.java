package com.ds.goroute.service;

import com.ds.goroute.dto.request.AssignActivityPlaceRequest;
import com.ds.goroute.dto.response.AdminUnlinkedActivityResponse;

import java.util.List;
import java.util.UUID;

/**
 * Admin queue of trip activities whose address names a spot the catalogue does not have,
 * the action that links one to a catalogue place, and taking one out of the queue.
 */
public interface ActivityPlaceAdminService {

    /**
     * {@code categories} keeps only activities of those categories; {@code excludedCategories}
     * drops those categories but keeps activities with no category. Either may be null or empty.
     */
    List<AdminUnlinkedActivityResponse> listUnlinked(String search, boolean dismissed, List<String> categories,
                                                     List<String> excludedCategories, int page, int size);

    long countUnlinked(String search, boolean dismissed, List<String> categories, List<String> excludedCategories);

    void assignPlace(UUID adminId, UUID activityId, AssignActivityPlaceRequest request);

    /** Takes the activity out of the queue without linking it: there is nothing to link. */
    void dismiss(UUID adminId, UUID activityId);

    /** Puts a dismissed activity back in the queue. */
    void restore(UUID adminId, UUID activityId);
}
