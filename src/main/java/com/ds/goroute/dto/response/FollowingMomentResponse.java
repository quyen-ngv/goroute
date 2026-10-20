package com.ds.goroute.dto.response;

import com.ds.goroute.type.FollowingPostType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One unread post of someone the viewer follows, for the moments popup. Exactly one of
 * {@code checkin} and {@code trip} is set, and it is the same card payload the feed returns.
 */
@Value
@Builder
public class FollowingMomentResponse {
    UUID notificationId;
    FollowingPostType postType;
    UUID postId;
    /** When the follower was told, which is when the post was published to them. */
    LocalDateTime createdAt;
    UserCheckinResponse checkin;
    PublicTripResponse trip;
}
