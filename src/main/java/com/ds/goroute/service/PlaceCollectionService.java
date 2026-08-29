package com.ds.goroute.service;

import com.ds.goroute.dto.request.AddCollectionItemRequest;
import com.ds.goroute.dto.request.UpsertPlaceCollectionRequest;
import com.ds.goroute.dto.response.PlaceCollectionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Collections of places, shareable by link (SOC-04).
 *
 * <p>The privacy rule that shapes the whole feature: sharing a collection reveals the
 * collection and nothing else. The saved list it was built from stays private, because
 * people save places they would not want anyone to see a list of.
 */
public interface PlaceCollectionService {

    PlaceCollectionResponse create(UUID ownerId, UpsertPlaceCollectionRequest request);

    PlaceCollectionResponse update(UUID ownerId, UUID collectionId, UpsertPlaceCollectionRequest request);

    void delete(UUID ownerId, UUID collectionId);

    PlaceCollectionResponse get(UUID viewerId, UUID collectionId);

    /** Reads a shared collection by link; un-publishing makes the old link stop working. */
    PlaceCollectionResponse getByShareSlug(String shareSlug);

    List<PlaceCollectionResponse> listMine(UUID ownerId, int page, int size);

    long countMine(UUID ownerId);

    PlaceCollectionResponse addItem(UUID ownerId, UUID collectionId, AddCollectionItemRequest request);

    PlaceCollectionResponse removeItem(UUID ownerId, UUID collectionId, UUID itemId);

    /** Order is part of the content, so the owner controls it. */
    PlaceCollectionResponse reorder(UUID ownerId, UUID collectionId, List<UUID> orderedItemIds);
}
