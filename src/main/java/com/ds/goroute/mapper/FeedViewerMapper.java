package com.ds.goroute.mapper;

import com.ds.goroute.feed.model.FeedViewerRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** What the ranked feed knows about the person looking at it (V245, V249). */
@Mapper
public interface FeedViewerMapper {

    /** The people the viewer follows, closest first (affinity, then most recently followed). */
    List<FeedViewerRows.Followed> selectFollowing(@Param("viewerId") UUID viewerId, @Param("limit") int limit);

    List<FeedViewerRows.Affinity> selectAffinity(@Param("viewerId") UUID viewerId, @Param("limit") int limit);

    /** The viewer's trips (owned or joined) running on {@code today} or starting by {@code until}. */
    List<FeedViewerRows.ContextTrip> selectContextTrips(@Param("viewerId") UUID viewerId,
                                                        @Param("today") LocalDate today,
                                                        @Param("until") LocalDate until);

    List<FeedViewerRows.ContextDestination> selectContextDestinations(@Param("tripIds") Collection<UUID> tripIds);

    /** Catalogue places on the itineraries of these trips. */
    List<UUID> selectItineraryPlaceIds(@Param("tripIds") Collection<UUID> tripIds, @Param("limit") int limit);

    /** Provinces the viewer wished for in their Passport, and those of their latest check-ins. */
    List<String> selectRelatedProvinces(@Param("viewerId") UUID viewerId, @Param("recentCheckins") int recentCheckins);

    String selectProvinceAt(@Param("latitude") double latitude, @Param("longitude") double longitude);

    String selectProvinceName(@Param("code") String code);

    /** The viewer's impressions since {@code since}, newest first. */
    List<FeedViewerRows.Seen> selectSeenSince(@Param("viewerId") UUID viewerId,
                                              @Param("since") LocalDateTime since,
                                              @Param("limit") int limit);

    /** Posts the given people liked, commented on or saved since {@code since}. */
    List<FeedViewerRows.SocialProof> selectSocialProof(@Param("userIds") Collection<UUID> userIds,
                                                       @Param("since") LocalDateTime since,
                                                       @Param("limit") int limit);

    // --- affinity rebuild (FeedAffinityJob) ---------------------------------------------

    int deleteAllAffinity();

    /**
     * Recomputes every viewer-author pair from the interactions since {@code since}: each
     * weighted and halved every {@code halfLifeDays}, pairs under {@code minScore} dropped,
     * at most {@code perViewer} authors kept per viewer.
     */
    int insertAffinity(@Param("since") LocalDateTime since,
                       @Param("halfLifeDays") double halfLifeDays,
                       @Param("likeWeight") double likeWeight,
                       @Param("commentWeight") double commentWeight,
                       @Param("saveWeight") double saveWeight,
                       @Param("openWeight") double openWeight,
                       @Param("dwellWeight") double dwellWeight,
                       @Param("dwellMs") int dwellMs,
                       @Param("minScore") double minScore,
                       @Param("perViewer") int perViewer);
}
