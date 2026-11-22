package com.ds.goroute.mapper;

import com.ds.goroute.entity.SavedCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SavedCategoryMapper {

    /** Creates the category for {@code name} unless the user already has one by that name, ignoring case. */
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("userId") UUID userId,
                       @Param("name") String name,
                       @Param("now") LocalDateTime now);

    SavedCategory findById(@Param("id") UUID id);

    /** The user's category whose name matches {@code name} ignoring case and surrounding spaces. */
    SavedCategory findByUserAndName(@Param("userId") UUID userId, @Param("name") String name);

    /** One category with its item count, newest item photo and last save time. */
    SavedCategory findSummaryById(@Param("id") UUID id);

    /**
     * The user's categories with their counts, most recently saved into first, at most {@code limit}.
     * {@code publicOnly} keeps the PUBLIC ones.
     */
    List<SavedCategory> findSummariesByUser(@Param("userId") UUID userId,
                                            @Param("publicOnly") boolean publicOnly,
                                            @Param("limit") int limit);

    /** Rewrites name, cover and visibility of a category the user owns; returns the rows changed. */
    int update(SavedCategory category);

    int deleteOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
