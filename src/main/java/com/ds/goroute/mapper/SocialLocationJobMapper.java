package com.ds.goroute.mapper;

import com.ds.goroute.entity.SocialLocationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Mapper
public interface SocialLocationJobMapper {
    void insert(SocialLocationJob job);

    void update(SocialLocationJob job);

    SocialLocationJob findById(@Param("id") UUID id);

    SocialLocationJob findByPythonJobId(@Param("pythonJobId") String pythonJobId);

    SocialLocationJob findReusableByUserIdAndSourceKey(@Param("userId") UUID userId,
                                                        @Param("sourceKey") String sourceKey);

    List<SocialLocationJob> findByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    List<SocialLocationJob> findCompletedByUserId(
            @Param("userId") UUID userId,
            @Param("ids") List<UUID> ids,
            @Param("limit") int limit);

    int countCreatedByUserSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    boolean lockUserSubmission(@Param("userId") UUID userId);

    boolean lockSubmissionQueue();

    int countQueued();

    int countActive();

    boolean tryDispatchLock();

    List<SocialLocationJob> claimQueued(@Param("limit") int limit);
}
