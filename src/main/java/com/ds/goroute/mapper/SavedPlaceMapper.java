package com.ds.goroute.mapper;

import com.ds.goroute.entity.SavedPlace;
import com.ds.goroute.entity.SavedPlaceMark;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.dto.response.SavedItemTripResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
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

    /** The user's items in one category (matched ignoring case), newest first, at most {@code limit}. */
    List<SavedPlace> findByCategoryKey(@Param("userId") UUID userId,
                                       @Param("category") String category,
                                       @Param("limit") int limit);

    /** Whether any of the user's items sit in a category of this name, ignoring case. */
    boolean existsByCategoryKey(@Param("userId") UUID userId, @Param("category") String category);

    /**
     * Within one category, removes every save of an item but its earliest. Items saved under
     * spellings that differ only in case are one category; renaming them to one spelling would
     * otherwise break the per-category unique index.
     */
    int deleteDuplicatesInCategory(@Param("userId") UUID userId, @Param("category") String category);

    int renameCategory(@Param("userId") UUID userId,
                       @Param("category") String category,
                       @Param("newCategory") String newCategory);

    int deleteByCategoryKey(@Param("userId") UUID userId, @Param("category") String category);

    /**
     * Deletes the user's place and food saves of these places: only those in {@code category}
     * (matched ignoring case) when it is set, else all of them.
     */
    int deletePlaceSaves(@Param("userId") UUID userId,
                         @Param("placeIds") List<String> placeIds,
                         @Param("category") String category);

    List<SavedPlaceMark> findMarksByUserId(@Param("userId") UUID userId);

    /** Keeps the first time when the place was already marked. */
    void markVisited(@Param("userId") UUID userId,
                     @Param("placeId") String placeId,
                     @Param("visitedAt") LocalDateTime visitedAt);

    void clearVisited(@Param("userId") UUID userId, @Param("placeId") String placeId);

    void markRemoved(@Param("userId") UUID userId,
                     @Param("placeIds") List<String> placeIds,
                     @Param("removedAt") LocalDateTime removedAt);
}
