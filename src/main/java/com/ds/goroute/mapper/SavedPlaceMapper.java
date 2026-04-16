package com.ds.goroute.mapper;

import com.ds.goroute.entity.SavedPlace;
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
                                   @Param("limit") Integer limit, 
                                   @Param("offset") Integer offset);
    
    SavedPlace findByUserIdAndPlaceId(@Param("userId") UUID userId, @Param("placeId") String placeId);
    
    void updateTags(@Param("id") UUID id, @Param("tags") String[] tags);
    
    void deleteById(@Param("id") UUID id);
}
