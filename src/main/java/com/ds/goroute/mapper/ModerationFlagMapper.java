package com.ds.goroute.mapper;

import com.ds.goroute.entity.ContentReport;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.ModerationFlag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * The shared review queue and its two write paths: automatic flags and user reports
 * (MOD-06, SOC-06a). Takedowns live here too because a takedown is the outcome of a
 * queue decision.
 */
@Mapper
public interface ModerationFlagMapper {

    /**
     * Raises a flag, or folds into the open one for the same content/source/category.
     * Repeat reports raise priority and the report count; they never remove content.
     */
    int upsert(ModerationFlag flag);

    ModerationFlag findById(@Param("id") UUID id);

    ModerationFlag findOpen(@Param("contentType") String contentType,
                            @Param("contentId") UUID contentId,
                            @Param("source") String source,
                            @Param("category") String category);

    List<ModerationFlag> findQueue(@Param("status") String status,
                                   @Param("contentType") String contentType,
                                   @Param("category") String category,
                                   @Param("source") String source,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    long countQueue(@Param("status") String status,
                    @Param("contentType") String contentType,
                    @Param("category") String category,
                    @Param("source") String source);

    int resolve(@Param("id") UUID id,
                @Param("status") String status,
                @Param("reviewedBy") UUID reviewedBy,
                @Param("resolutionNote") String resolutionNote);

    int insertReport(ContentReport report);

    ContentReport findReport(@Param("contentType") String contentType,
                             @Param("contentId") UUID contentId,
                             @Param("reporterId") UUID reporterId);

    List<ContentReport> findReportsForContent(@Param("contentType") String contentType,
                                              @Param("contentId") UUID contentId);

    int resolveReportsForFlag(@Param("flagId") UUID flagId, @Param("status") String status);

    int insertTakedown(ContentTakedown takedown);

    ContentTakedown findActiveTakedown(@Param("contentType") String contentType,
                                       @Param("contentId") UUID contentId);

    List<UUID> findActiveTakedownIds(@Param("contentType") String contentType,
                                     @Param("contentIds") List<UUID> contentIds);

    int restoreTakedown(@Param("contentType") String contentType,
                        @Param("contentId") UUID contentId,
                        @Param("restoredBy") UUID restoredBy);
}
