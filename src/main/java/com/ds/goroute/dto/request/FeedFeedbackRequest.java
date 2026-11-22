package com.ds.goroute.dto.request;

import com.ds.goroute.type.FeedFeedbackKind;
import com.ds.goroute.type.FeedItemType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * "Hide this post" needs {@code itemType} + {@code itemId}; "hide posts from this person"
 * needs {@code targetUserId}. The service rejects a request missing the half its kind needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedFeedbackRequest {

    @NotNull
    private FeedFeedbackKind kind;

    private FeedItemType itemType;

    private UUID itemId;

    private UUID targetUserId;
}
