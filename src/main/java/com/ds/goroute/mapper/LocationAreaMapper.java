package com.ds.goroute.mapper;

import com.ds.goroute.dto.LocationAreaCandidateRow;
import com.ds.goroute.enums.LocationAreaTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationAreaMapper {

    /** Total rows for a target, whether or not they can ever be mapped. */
    long countTotal(@Param("target") LocationAreaTarget target);

    /** Rows that already carry an area. */
    long countMapped(@Param("target") LocationAreaTarget target);

    /**
     * Fills every row whose coordinates fall inside some area's radius, in one statement.
     * Returns how many rows changed.
     */
    int assignByCoordinates(@Param("target") LocationAreaTarget target);

    /** Coordinate pass for a single place that has no area yet. Returns 1 if it was filled. */
    int assignPlaceByCoordinates(@Param("id") UUID id);

    /** Copies the area of the linked Place onto hotels that have none. */
    int assignHotelsFromPlace();

    /** Matches food rows on the city slug vocabulary shared with location_images. */
    int assignFoodScoresByCitySlug();

    /**
     * Rows still unmapped after the coordinate pass, with the text the name fallback
     * should search. Bounded by {@code limit} so one run cannot load an unbounded set.
     */
    List<LocationAreaCandidateRow> findUnmapped(@Param("target") LocationAreaTarget target,
                                                @Param("limit") int limit);

    /** The same candidate text as {@link #findUnmapped}, for one row; null once it has an area. */
    LocationAreaCandidateRow findUnmappedById(@Param("target") LocationAreaTarget target,
                                              @Param("id") UUID id);

    int assignArea(@Param("target") LocationAreaTarget target,
                   @Param("id") UUID id,
                   @Param("locationImageId") UUID locationImageId);

    /**
     * Operator override for one row, whether or not it already carries an area; a null
     * {@code locationImageId} clears it.
     *
     * <p>Separate from {@link #assignArea} on purpose: that one keeps its {@code IS NULL}
     * guard so the auto-map job can never overwrite a choice made by hand here.
     */
    int setArea(@Param("target") LocationAreaTarget target,
                @Param("id") UUID id,
                @Param("locationImageId") UUID locationImageId);

    /** Keeps the tour link table in step with the primary area after an auto-map run. */
    int syncActivityAreaLinks();
}
