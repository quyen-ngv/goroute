package com.ds.goroute.service;

import com.ds.goroute.dto.response.SocialSearchResponse;
import com.ds.goroute.type.SocialSearchType;

import java.util.UUID;

/**
 * The Feed search (epic 12 §4.3): people, public check-ins and public trips by keyword, ranked
 * by Lucene ({@link SocialSearchIndexService}) and checked against the database before they are
 * shown — public, not removed or taken down, author still has an account, no block either way.
 */
public interface SocialSearchService {

    /**
     * Page {@code page} of {@code size} results of {@code type} for {@code query}. A keyword
     * shorter than two characters finds nothing. The viewer is never among the people found;
     * their own posts are.
     */
    SocialSearchResponse search(UUID viewerId, String query, SocialSearchType type, int page, int size);
}
