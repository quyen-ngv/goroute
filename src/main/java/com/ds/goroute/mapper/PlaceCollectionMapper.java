package com.ds.goroute.mapper;

import com.ds.goroute.entity.PlaceCollection;
import com.ds.goroute.entity.PlaceCollectionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlaceCollectionMapper {

    int insert(PlaceCollection collection);

    int update(PlaceCollection collection);

    PlaceCollection findById(@Param("id") UUID id);

    /**
     * Reads a collection by its share link.
     *
     * <p>Filters on visibility in the query rather than after it: un-publishing has to mean
     * the old link stops working, and a check the caller has to remember is a check that
     * eventually gets forgotten.
     */
    PlaceCollection findPublicBySlug(@Param("shareSlug") String shareSlug);

    List<PlaceCollection> findByOwner(@Param("ownerId") UUID ownerId,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    long countByOwner(@Param("ownerId") UUID ownerId);

    int markRemoved(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    int incrementViewCount(@Param("id") UUID id);

    int insertItem(PlaceCollectionItem item);

    int deleteItem(@Param("collectionId") UUID collectionId, @Param("itemId") UUID itemId);

    int updateItemPosition(@Param("collectionId") UUID collectionId,
                           @Param("itemId") UUID itemId,
                           @Param("position") int position);

    /** Only the items of this collection are ever selected, never the owner's saved list. */
    List<PlaceCollectionItem> findItems(@Param("collectionId") UUID collectionId);

    int countItems(@Param("collectionId") UUID collectionId);

    int refreshItemCount(@Param("collectionId") UUID collectionId);
}
