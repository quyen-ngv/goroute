package com.ds.goroute.service;

import com.ds.goroute.dto.request.FollowingMomentRefRequest;
import com.ds.goroute.dto.response.FollowingMomentsResponse;

import java.util.List;
import java.util.UUID;

/**
 * The "moments" popup (epic 12): what the people the viewer follows posted that the viewer has
 * not seen yet. Seen means the follower notification of that post is read, wherever it was read.
 */
public interface FollowingMomentsService {

    /**
     * Unread follower posts of the last SOCIAL.MOMENTS_WINDOW_HOURS, newest first, at most
     * {@link #MAX_ITEMS}. Rows whose post is gone (deleted, private, taken down, author blocked)
     * are marked read on the way, so they never come back.
     */
    FollowingMomentsResponse unread(UUID viewerId);

    /** Marks the viewer's follower notifications of these posts read; returns how many changed. */
    int markRead(UUID viewerId, List<FollowingMomentRefRequest> posts);

    int MAX_ITEMS = 20;
}
