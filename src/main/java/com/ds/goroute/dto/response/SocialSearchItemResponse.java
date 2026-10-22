package com.ds.goroute.dto.response;

import com.ds.goroute.type.SocialSearchType;
import lombok.Builder;
import lombok.Data;

/**
 * One Feed search result. Exactly one of {@link #user}, {@link #checkin} and {@link #trip} is
 * set, by {@link #type}; posts carry the same card payload the Feed draws.
 */
@Data
@Builder
public class SocialSearchItemResponse {
    /** {@code USER}, {@code CHECKIN} or {@code TRIP}; never {@code ALL}. */
    private SocialSearchType type;
    private DiscoverUserResponse user;
    private UserCheckinResponse checkin;
    private PublicTripResponse trip;
}
