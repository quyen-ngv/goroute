package com.ds.goroute.localevent.persistence;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Console list filters. Empty lists mean "any"; {@code sortKey} is a closed whitelist value. */
@Getter
@Builder
public class LocalEventAdminSearchParams {
    /** The search text normalised like title_norm, with LIKE wildcards escaped. */
    private final String searchNorm;
    /** The raw search text, matched exactly against external_id. */
    private final String search;
    private final List<String> publishStatuses;
    private final List<String> eventStatuses;
    private final List<String> categories;
    private final String source;
    private final String provinceCode;
    private final LocalDateTime startFrom;
    private final LocalDateTime startTo;
    private final boolean duplicatesOnly;
    /** One of startAt, createdAt, updatedAt. */
    private final String sortKey;
    private final boolean descending;
    private final int limit;
    private final int offset;
}
