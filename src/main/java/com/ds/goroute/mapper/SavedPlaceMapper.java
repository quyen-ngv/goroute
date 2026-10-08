package com.ds.goroute.mapper;

import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.dto.response.SavedItemTripResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SavedPlaceMapper {
    
    void insert(SavedPlace savedPlace);
    
    SavedPlace findById(@Param("id") UUID id);
    
    List<SavedPlace> findByUserId(@Param("userId") UUID userId, 
                                   @Param("category") String category,
                                   @Param("itemType") String itemType,
                                   @Param("limit") Integer limit, 
                                   @Param("offset") Integer offset);

    /** The user's saved items, newest first: at most {@code limit}, or all of them when {@code limit} is null. */
    List<SavedPlace> findAllByUserId(@Param("userId") UUID userId, @Param("limit") Integer limit);

    /**
     * The user's completed social-location jobs, newest first, with only id, createdAt, completedAt and a
     * cut-down resultPayload (the fields the recent-saves list reads) populated.
     */
    List<SocialLocationJob> findSocialSavedPlacesByUserId(@Param("userId") UUID userId);

    List<SavedItemTripResponse> findTripItemsByUserId(@Param("userId") UUID userId);
    
    SavedPlace findByUserIdAndPlaceId(@Param("userId") UUID userId,
                                      @Param("placeId") String placeId,
                                      @Param("itemType") String itemType,
                                      @Param("category") String category);
    
    void updateTags(@Param("id") UUID id, @Param("tags") String[] tags);

    void updateCategory(@Param("id") UUID id, @Param("category") String category);
    
    void deleteById(@Param("id") UUID id);
}
