package com.ds.goroute.dto;

import com.ds.goroute.type.SocialSearchType;

import java.util.List;
import java.util.UUID;

/**
 * One page of the Feed search index, best match first.
 *
 * @param hits      what matched, by kind and id; the caller still checks each is visible
 * @param totalHits how many documents match in all (a lower bound past a thousand)
 */
public record SocialSearchHits(List<Hit> hits, long totalHits) {

    public static final SocialSearchHits EMPTY = new SocialSearchHits(List.of(), 0);

    /** A matched user, check-in or trip. */
    public record Hit(SocialSearchType type, UUID id) {
    }
}
