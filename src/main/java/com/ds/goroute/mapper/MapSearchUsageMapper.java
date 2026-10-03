package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.UUID;

@Mapper
public interface MapSearchUsageMapper {

    /**
     * Counts one forwarded call if the user is still under {@code dailyLimit} today.
     *
     * @return 1 when the call was counted, 0 when the daily limit is already used up
     */
    int claimRequest(@Param("userId") UUID userId,
                     @Param("usageDate") LocalDate usageDate,
                     @Param("operation") String operation,
                     @Param("dailyLimit") int dailyLimit);

    int recordRejected(@Param("userId") UUID userId,
                       @Param("usageDate") LocalDate usageDate,
                       @Param("operation") String operation);

    int deleteBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
