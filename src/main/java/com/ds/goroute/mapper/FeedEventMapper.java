package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** feed_events (V267) upkeep. Rows are written by FeedEventLogger with COPY, not through MyBatis. */
@Mapper
public interface FeedEventMapper {

    /**
     * Makes the daily partitions up to {@code daysAhead} days from today and drops those older
     * than {@code keepDays}; returns how many were dropped.
     */
    int maintainPartitions(@Param("daysAhead") int daysAhead, @Param("keepDays") int keepDays);
}
