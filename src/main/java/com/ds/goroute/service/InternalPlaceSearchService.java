package com.ds.goroute.service;

import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;

/** Social-video place search run by the backend on behalf of the scrape worker. */
public interface InternalPlaceSearchService {

    InternalPlaceSearchResponse search(InternalPlaceSearchRequest request);
}
