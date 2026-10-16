package com.ds.goroute.dto.response;

import com.ds.goroute.type.SavedPostType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One saved post as it is now, not as it was when saved: {@code checkin} or {@code review} is
 * the same payload the feed returns. When the post was deleted, taken down, made private or
 * its author and the viewer blocked one another, {@code available} is false and both are null,
 * so the list can say the post is gone and offer to remove it.
 */
@Value
@Builder
public class SavedPostResponse {
    SavedPostType postType;
    UUID postId;
    LocalDateTime savedAt;
    boolean available;
    UserCheckinResponse checkin;
    UserReviewResponse review;
}
