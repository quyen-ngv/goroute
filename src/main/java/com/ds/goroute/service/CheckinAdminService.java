package com.ds.goroute.service;

import com.ds.goroute.dto.request.AssignCheckinPlaceRequest;
import com.ds.goroute.dto.request.HideCheckinRequest;
import com.ds.goroute.dto.response.AdminCheckinResponse;
import com.ds.goroute.dto.response.CheckinLocationHistoryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Admin console for individual check-ins: browse them and hide/show one directly,
 * without needing a user report or a moderation-queue flag to exist first.
 */
public interface CheckinAdminService {

    List<AdminCheckinResponse> list(String search, UUID userId, Boolean hidden, int page, int size);

    long count(String search, UUID userId, Boolean hidden);

    /** Hides the check-in everywhere it is public. The row itself is never deleted. */
    void hide(UUID adminId, UUID checkinId, HideCheckinRequest request);

    /** Reverses a hide decision. */
    void show(UUID adminId, UUID checkinId);

    /**
     * Moves a check-in onto the catalogue place it actually happened at.
     *
     * <p>The original location is snapshotted first, the score the author gave follows the
     * check-in to the new place, and the passport entry is re-pointed with it.
     */
    void assignPlace(UUID adminId, UUID checkinId, AssignCheckinPlaceRequest request);

    /** Every location this check-in has carried, newest first. */
    List<CheckinLocationHistoryResponse> locationHistory(UUID checkinId);
}
