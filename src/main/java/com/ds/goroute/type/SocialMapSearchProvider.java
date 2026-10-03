package com.ds.goroute.type;

/**
 * Who looks a social-video place up on Google Maps (config {@code SOCIAL_LOCATION.MAP_SEARCH_PROVIDER}).
 * The name travels to the scrape worker as {@code map_search_provider}.
 */
public enum SocialMapSearchProvider {
    /** The worker drives Google Maps in its own browser. The default. */
    SCRAPE,
    /** The worker asks the backend, which calls Google Places API with its key. */
    GOOGLE
}
