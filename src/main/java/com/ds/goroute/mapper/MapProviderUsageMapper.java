package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** System-wide count of paid map calls, for the budget ceiling (epic 06). */
@Mapper
public interface MapProviderUsageMapper {

    /**
     * Counts one call in the period if it is still under {@code cap}, in one statement so two
     * concurrent calls cannot both take the last one.
     *
     * @return the period's count after this call, or null when the cap was already reached
     */
    Long claim(@Param("periodKind") String periodKind,
               @Param("periodStart") LocalDate periodStart,
               @Param("api") String api,
               @Param("cap") long cap);

    /** Gives back a call claimed by {@link #claim} that turned out not to be needed. */
    int release(@Param("periodKind") String periodKind,
                @Param("periodStart") LocalDate periodStart,
                @Param("api") String api);

    /** @return the period's refused count after this one */
    Long recordRefused(@Param("periodKind") String periodKind,
                       @Param("periodStart") LocalDate periodStart,
                       @Param("api") String api);

    /** Rows of one period start, for the console usage page. */
    List<Map<String, Object>> findPeriod(@Param("periodKind") String periodKind,
                                         @Param("periodStart") LocalDate periodStart);

    /** Everyone holding a console role; operators are told about spam and the budget. */
    List<UUID> findAdminUserIds();
}
