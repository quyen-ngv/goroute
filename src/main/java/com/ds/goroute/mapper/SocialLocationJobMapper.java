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

    int updateIfStatus(@Param("job") SocialLocationJob job,
                       @Param("expectedStatus") String expectedStatus);

    SocialLocationJob findById(@Param("id") UUID id);

    SocialLocationJob findByPythonJobId(@Param("pythonJobId") String pythonJobId);

    SocialLocationJob findReusableByUserIdAndSourceKey(@Param("userId") UUID userId,
                                                        @Param("sourceKey") String sourceKey);

    List<SocialLocationJob> findByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int markDeletedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    List<SocialLocationJob> findCompletedByUserId(
            @Param("userId") UUID userId,
            @Param("ids") List<UUID> ids,
            @Param("limit") int limit);

    List<SocialLocationJob> findAllCompletedByUserId(@Param("userId") UUID userId);

    List<SocialLocationJob> findCompletedForVideoLinkBackfill(
            @Param("limit") int limit,
            @Param("offset") int offset);

    int countCreatedByUserSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    boolean lockUserSubmission(@Param("userId") UUID userId);

    boolean lockSubmissionQueue();

    int countQueued();

    int countActive();

    List<SocialLocationJob> findStaleDispatching(@Param("cutoff") LocalDateTime cutoff,
                                                  @Param("limit") int limit);

    List<SocialLocationJob> findProcessingForReconciliation(
            @Param("reconcileBefore") LocalDateTime reconcileBefore,
            @Param("limit") int limit);

    boolean tryDispatchLock();

    List<SocialLocationJob> claimQueued(@Param("limit") int limit);
}
