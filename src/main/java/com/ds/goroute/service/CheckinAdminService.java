package com.ds.goroute.service;

import com.ds.goroute.dto.request.AssignCheckinPlaceRequest;
import com.ds.goroute.dto.request.HideCheckinRequest;
import com.ds.goroute.dto.response.AdminCheckinResponse;
import com.ds.goroute.dto.response.AdminUnlinkedCheckinResponse;
import com.ds.goroute.dto.response.CheckinLocationHistoryResponse;

import java.util.List;
import java.util.UUID;

/**
 * Admin console for individual check-ins: browse them and hide/show one directly,
 * without needing a user report or a moderation-queue flag to exist first.
 */
public interface CheckinAdminService {

    /**
     * @param needsPlace only check-ins with no catalogue place that nobody marked as "not a
     *                   place" (quest checkpoints excluded) -- the queue for {@link #assignPlace}
     */
    List<AdminCheckinResponse> list(String search, UUID userId, Boolean hidden, boolean needsPlace,
                                    int page, int size);

    long count(String search, UUID userId, Boolean hidden, boolean needsPlace);

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

    /** The same queue as {@code needsPlace}, for the place-linking agent: no author details. */
    List<AdminUnlinkedCheckinResponse> listUnlinked(int page, int size);

    /**
     * Takes a check-in out of the queue for a place: an approver said it names no real place
     * ("ăn trưa", a ward, a journey). The check-in itself does not change.
     */
    void dismissPlace(UUID adminId, UUID checkinId);

    /** Every location this check-in has carried, newest first. */
    List<CheckinLocationHistoryResponse> locationHistory(UUID checkinId);
}
