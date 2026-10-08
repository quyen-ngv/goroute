package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripHelpfulVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TripHelpfulVoteMapper {
    void insert(TripHelpfulVote vote);

    void update(TripHelpfulVote vote);

    void delete(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    TripHelpfulVote findByTripIdAndUserId(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    /** One user's votes across a page of trips. */
    List<TripHelpfulVote> findByTripIdsAndUserId(@Param("tripIds") List<UUID> tripIds, @Param("userId") UUID userId);

    int countByTripIdAndIsHelpful(@Param("tripId") UUID tripId, @Param("isHelpful") boolean isHelpful);
}
