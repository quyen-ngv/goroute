package com.ds.goroute.mapper;

import com.ds.goroute.entity.FollowerFanoutLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface FollowerFanoutLogMapper {

    int insert(FollowerFanoutLog log);

    int updateProgress(FollowerFanoutLog log);

    int finish(@Param("id") UUID id,
               @Param("status") String status,
               @Param("error") String error);

    boolean existsPushWithinMinutes(@Param("authorId") UUID authorId,
                                    @Param("minutes") int minutes);

    int countHeldBackSinceLastPush(@Param("authorId") UUID authorId);
}
