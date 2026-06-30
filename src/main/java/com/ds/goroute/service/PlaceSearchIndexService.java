package com.ds.goroute.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.ds.goroute.entity.Place;

public interface PlaceSearchIndexService {

    void indexPlace(Place place);

    void deletePlace(UUID id);

    void triggerReindex();

    List<UUID> searchTitleIds(String query, int maxResults) throws IOException;
}
