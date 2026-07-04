package com.ds.goroute.mapper;

import com.ds.goroute.entity.*;
import com.ds.goroute.type.ContributionGroupStatus;
import com.ds.goroute.type.ContributionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceContributionMapper {

    void insertGroup(PlaceContributionGroup group);

    void updateGroup(PlaceContributionGroup group);

    PlaceContributionGroup findGroupById(@Param("id") UUID id);

    PlaceContributionGroup findActiveGroupByUrlHash(
            @Param("normalizedUrlHash") String normalizedUrlHash,
            @Param("statuses") List<ContributionGroupStatus> statuses);

    List<PlaceContributionGroup> findGroupsByStatus(
            @Param("status") ContributionGroupStatus status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countGroupsByStatus(@Param("status") ContributionGroupStatus status);

    List<PlaceContributionGroup> findGroupsInScrapingStatus();

    void insertContribution(PlaceContribution contribution);

    void updateContribution(PlaceContribution contribution);

    PlaceContribution findContributionById(@Param("id") UUID id);

    PlaceContribution findContributionByUserAndGroup(
            @Param("userId") UUID userId,
            @Param("groupId") UUID groupId);

    List<PlaceContribution> findContributionsByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int countContributionsByUserId(@Param("userId") UUID userId);

    List<PlaceContribution> findContributionsByGroupId(@Param("groupId") UUID groupId);

    void insertPendingReview(PendingContributionReview review);

    void updatePendingReview(PendingContributionReview review);

    PendingContributionReview findPendingReviewByContributionId(@Param("contributionId") UUID contributionId);

    void deletePendingReviewByContributionId(@Param("contributionId") UUID contributionId);

    void deleteContributionById(@Param("id") UUID id);

    void deleteGroupById(@Param("id") UUID id);

    List<PendingContributionReview> findPendingReviewsByGroupId(@Param("groupId") UUID groupId);

    void insertContributor(PlaceContributor contributor);

    List<PlaceContributor> findContributorsByPlaceId(@Param("placeId") UUID placeId);

    List<PlaceContributor> findContributorsByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int countContributedPlacesByUserId(@Param("userId") UUID userId);

    void insertImportLog(PlaceContributionImportLog log);

    PlaceContributionImportLog findImportLogByGroupId(@Param("groupId") UUID groupId);

    PlaceContributionImportLog findImportLogByGorouteJobId(@Param("gorouteJobId") UUID gorouteJobId);

    Place findPlaceByCid(@Param("cid") String cid);

    Place findPlaceByGoogleMapsLink(@Param("googleMapsLink") String googleMapsLink);
}
