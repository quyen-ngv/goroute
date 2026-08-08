package com.ds.goroute.mapper;

import com.ds.goroute.entity.AiTripGenerationEvent;
import com.ds.goroute.entity.AiTripGenerationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

@Mapper
public interface AiTripGenerationMapper {
    void insertJob(AiTripGenerationJob job);
    AiTripGenerationJob findById(@Param("id") UUID id);
    AiTripGenerationJob findByIdForUpdate(@Param("id") UUID id);
    AiTripGenerationJob findByUserAndKey(@Param("userId") UUID userId, @Param("key") String key);
    AiTripGenerationJob findActiveByUser(@Param("userId") UUID userId);
    int claimForDispatch(@Param("id") UUID id, @Param("attemptId") String attemptId);
    void updateProgress(@Param("id") UUID id, @Param("attemptId") String attemptId,
                        @Param("status") String status, @Param("stage") String stage,
                        @Param("progress") int progress, @Param("error") String error);
    void markCompleted(@Param("id") UUID id, @Param("attemptId") String attemptId,
                       @Param("tripId") UUID tripId);
    int markTerminalAndRelease(@Param("id") UUID id, @Param("status") String status,
                               @Param("error") String error);
    void insertEvent(AiTripGenerationEvent event);
    List<AiTripGenerationEvent> findEventsAfter(@Param("jobId") UUID jobId,
                                                @Param("afterId") long afterId);
}
