package com.ds.goroute.service;

import com.ds.goroute.dto.SocialSearchHits;
import com.ds.goroute.type.SocialSearchType;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Lucene index behind the Feed search (epic 12 §4.4): people, public check-ins, public
 * trips, tours and events in one index (places and stays come from the place index). It
 * follows the database on its own — rebuilt in the background at start and every night,
 * caught up every 30 seconds from the rows changed since — so no write path has to tell it
 * anything. It may lag or hold a post that is no longer visible; callers check
 * every hit against the database before showing it.
 */
public interface SocialSearchIndexService {

    /** Most documents a search pages through: {@code offset + limit} beyond this finds nothing more. */
    int MAX_RESULT_WINDOW = 500;

    /** {@code tourVariant} of the partner products in the marketplace. */
    String TOUR_MARKETPLACE = "MARKETPLACE";
    /** {@code tourVariant} of the imported tour catalogue. */
    String TOUR_CATALOGUE = "CATALOGUE";

    /**
     * Documents of the given kinds matching {@code keyword} well enough to show, best match
     * first, skipping the first {@code offset}: typo matches only when nothing matches as
     * typed, and never the weak tail of the best match (epic 12 §4.4). {@code namesOnly}
     * matches the name and username alone, not bios, captions or descriptions.
     * {@code excludeUserId} (nullable) leaves that person out of people results;
     * {@code tourVariant} (nullable) keeps only the tours of that source. A keyword with
     * nothing searchable in it finds nothing.
     */
    SocialSearchHits search(String keyword, Set<SocialSearchType> kinds, UUID excludeUserId,
                            String tourVariant, boolean namesOnly, int offset, int limit) throws IOException;

    /**
     * How many people, check-ins, trips, tours (of {@code tourVariant} when given) and events
     * each kind's own {@link #search} keeps for {@code keyword}, before visibility checks.
     */
    Map<SocialSearchType, Integer> countByKind(String keyword, UUID excludeUserId, String tourVariant)
            throws IOException;

    /** Starts a full rebuild in the background; false when one is already running. */
    boolean triggerReindex();
}
