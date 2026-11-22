package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The moments popup. {@code enabled} is false while the feature is off for this viewer
 * (SOCIAL.FOLLOWER_FANOUT_ENABLED off and not a beta user); {@code items} is then empty.
 */
@Value
@Builder
public class FollowingMomentsResponse {
    boolean enabled;
    List<FollowingMomentResponse> items;
}
