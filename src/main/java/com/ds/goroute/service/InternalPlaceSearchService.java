package com.ds.goroute.service;

import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;

/** Google place search run by the backend on behalf of the scrape worker. */
public interface InternalPlaceSearchService {

    /** True when the backend holds a Google Places key, so the worker may be told to use it. */
    boolean isAvailable();

    InternalPlaceSearchResponse search(InternalPlaceSearchRequest request);
}
