package com.ds.goroute.dto.response;

import com.ds.goroute.type.SocialSearchType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** One page of the Feed search (epic 12 §4.3). */
@Data
@Builder
public class SocialSearchResponse {
    /** The keyword as searched: trimmed and cut to the maximum length. */
    private String query;
    private SocialSearchType type;
    private int page;
    private int size;
    /** Whether a later page may hold more; a page can come back short when results were hidden. */
    private boolean hasMore;
    /** Page 0 only, null after it. */
    private SocialSearchCountsResponse counts;
    /** {@code ALL}, page 0 only: the best few people, shown above the posts. Empty otherwise. */
    private List<DiscoverUserResponse> users;
    /** People for {@code USER}; check-ins and/or trips otherwise, best match first. */
    private List<SocialSearchItemResponse> items;
}
