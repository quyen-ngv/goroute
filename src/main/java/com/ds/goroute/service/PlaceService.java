package com.ds.goroute.service;

import com.ds.goroute.dto.request.BatchUpdatePlaceImagesRequest;
import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.AdminPlaceResponse;
import com.ds.goroute.dto.response.AdminPlacePageResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.utils.LuceneKeywordQuery;
import com.ds.goroute.type.PlaceSearchSort;
import com.ds.goroute.type.PlaceTrendingPeriod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PlaceService {

    /**
     * Import place from Google Maps data
     */
    PlaceResponse importPlace(ImportPlaceRequest request);

    /**
     * Get place by ID
     */
    PlaceResponse getPlaceById(UUID id);

    /**
     * Import multiple places
     */
    List<PlaceResponse> importPlaces(List<ImportPlaceRequest> requests);

    /**
     * Get place by Google Place ID
     */
    PlaceResponse getPlaceByGoogleId(String placeId);

    /**
     * Type-ahead/catalogue page. A blank {@code search} lists newest first; a UUID returns
     * that place; an exact Google place id returns that place; other text goes through the
     * Lucene name index (accent-folded, translated names). Text shorter than two characters
     * returns nothing.
     *
     * @param includeInactive false limits the result to places a guest may see
     */
    List<PlaceResponse> getAllPlaces(String search, List<String> placeGroups, boolean includeInactive,
                                     int page, int size);

    /**
     * {@link #getAllPlaces(String, List, boolean, int, int)} leaning towards a point: names that
     * match alike come nearer first, nothing is left out for being far, and each place carries
     * {@code distance} from the point. Null coordinates behave as the plain call.
     */
    List<PlaceResponse> getAllPlaces(String search, List<String> placeGroups, boolean includeInactive,
                                     int page, int size, BigDecimal latitude, BigDecimal longitude);

    /**
     * List every place the detail-refresh worker may re-scrape, newest-stale first.
     *
     * <p>Unlike {@link #getAllPlaces} this is not a page of the catalogue: the worker needs
     * the whole eligible set in one call, filtered by visibility rather than by recency.
     */
    Map<String, Object> getDetailRefreshCandidates(UUID placeId, boolean includeInactive, Integer maxPlaces);

    AdminPlacePageResponse getAdminPlaces(String search, List<String> placeGroups, List<String> subTypes,
                                          List<String> visibilityStatus,
                                          List<String> trustLevel, List<java.util.UUID> locationImageIds,
                                          String sort, boolean descending, int page, int size);

    AdminPlaceResponse getAdminPlaceById(UUID id);

    List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                     BigDecimal radius, String category, List<String> placeGroups,
                                     BigDecimal minRating, int page, int size);

    List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                     BigDecimal radius, String category, List<String> placeGroups,
                                     BigDecimal minRating, String citySlug, List<UUID> foodIds,
                                     Boolean excludeLinkedFoodPlaces, boolean includeInactive, int page, int size);

    /**
     * Cached ({@code placeSearch}) with the point rounded in the key, so a hit may hold the
     * results, and distances, of another caller up to a few metres away. Callers that show
     * {@code distance} pass the result through {@link #withDistanceFrom}.
     */
    List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude,
                                     BigDecimal radius, String category, List<String> placeGroups,
                                     List<String> subTypes,
                                     BigDecimal minRating, PlaceSearchSort sort, String citySlug, List<UUID> foodIds,
                                     Boolean excludeLinkedFoodPlaces, boolean includeInactive,
                                     Float minLuceneScore, int page, int size);

    /**
     * The tier {@link #searchPlaces} matches {@code keyword} at within {@code radius} km of the
     * point, its kind filter aside: {@link LuceneKeywordQuery.Match#ANY_WORD} means no name
     * there has the words together. Null without a searchable keyword.
     */
    LuceneKeywordQuery.Match areaKeywordTier(String keyword, BigDecimal latitude, BigDecimal longitude,
                                             BigDecimal radius, List<String> placeGroups);

    /**
     * Public places named like {@code keyword} beyond {@code radius} km of the point, the ones
     * {@link #searchPlaces} of that circle can never return: best match first, then the most
     * prominent, at most {@code maxHits}, {@code distance} from the point. Not cached.
     */
    List<PlaceResponse> searchPlacesByNameOutside(String keyword, BigDecimal latitude, BigDecimal longitude,
                                                  BigDecimal radius, List<String> placeGroups, int maxHits);

    /**
     * How many places within {@code radius} km of the point carry each sub type that fits
     * {@code placeGroups} (every sub type when none is given), then {@code OTHER}: those with no
     * sub type. Counted in the same index and with the same filters as {@link #searchPlaces}, so
     * a chip's number is what choosing it lists.
     */
    Map<String, Integer> countPlacesBySubType(BigDecimal latitude, BigDecimal longitude, BigDecimal radius,
                                             List<String> placeGroups, boolean includeInactive);

    /**
     * The public places within {@code radiusKm} of the point that trips and check-ins used most
     * over {@code period} (rolling back from now), most used first: the same trip and check-in
     * counts that rank Trip Detail's suggestions, counted inside the window only. Places nobody
     * used in the window are left out, so the list can be shorter than {@code size} or empty.
     */
    List<PlaceResponse> getTrendingPlaces(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                          List<String> placeGroups, PlaceTrendingPeriod period, int size);

    /**
     * Copies of {@code places} with {@code distance} measured from this exact point, order kept.
     * The inputs are never modified: they may be entries of the shared search cache.
     */
    static List<PlaceResponse> withDistanceFrom(List<PlaceResponse> places, BigDecimal latitude,
                                                BigDecimal longitude) {
        return places.stream()
                .map(place -> place.toBuilder()
                        .distance(GeoDistance.kilometresOrFarAway(
                                latitude, longitude, place.getLatitude(), place.getLongitude()))
                        .build())
                .toList();
    }

    /**
     * Get reviews for a place with pagination
     */
    List<PlaceReviewResponse> getPlaceReviews(UUID placeId, int page, int size);

    /**
     * Update place information
     */
    PlaceResponse updatePlace(UUID id, UpdatePlaceRequest request);

    /**
     * Delete place and its reviews
     */
    void deletePlace(UUID id);
    
    /**
     * Batch update place images (thumbnail & images)
     */
    Map<String, Object> batchUpdatePlaceImages(BatchUpdatePlaceImagesRequest request);

    /**
     * Starts a full Lucene rebuild in the background.
     *
     * @return false when a rebuild is already running (nothing new was started)
     */
    boolean triggerSearchReindex();

    /** Re-reads the place and writes it to the search index (after a score written elsewhere). */
    void reindexPlace(UUID placeId);

    /** Drops every cached place search; import jobs call it once when they finish. */
    void evictSearchCache();
}
