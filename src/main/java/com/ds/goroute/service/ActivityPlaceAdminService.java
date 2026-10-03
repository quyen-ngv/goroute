package com.ds.goroute.service;

import com.ds.goroute.dto.request.AssignActivityPlaceRequest;
import com.ds.goroute.dto.response.AdminUnlinkedActivityResponse;

import java.util.List;
import java.util.UUID;

/**
 * Admin queue of trip activities whose address names a spot the catalogue does not have,
 * and the action that links one to a catalogue place.
 */
public interface ActivityPlaceAdminService {

    List<AdminUnlinkedActivityResponse> listUnlinked(String search, int page, int size);

    long countUnlinked(String search);

    void assignPlace(UUID adminId, UUID activityId, AssignActivityPlaceRequest request);
}
