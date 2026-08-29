package com.ds.goroute.mapper;

import com.ds.goroute.entity.ModerationTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** Administrable term list and its change log (MOD-02). */
@Mapper
public interface ModerationTermMapper {

    /** Snapshot used by the matcher; exemptions included. */
    List<ModerationTerm> findAllActive();

    List<ModerationTerm> findAdmin(@Param("query") String query,
                                   @Param("category") String category,
                                   @Param("isExemption") Boolean isExemption,
                                   @Param("active") Boolean active,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    long countAdmin(@Param("query") String query,
                    @Param("category") String category,
                    @Param("isExemption") Boolean isExemption,
                    @Param("active") Boolean active);

    ModerationTerm findById(@Param("id") UUID id);

    int insert(ModerationTerm term);

    int update(ModerationTerm term);

    int delete(@Param("id") UUID id);

    int insertAudit(@Param("termId") UUID termId,
                    @Param("action") String action,
                    @Param("beforeValue") String beforeValue,
                    @Param("afterValue") String afterValue,
                    @Param("changedBy") UUID changedBy);

    List<java.util.Map<String, Object>> findAudit(@Param("termId") UUID termId,
                                                  @Param("limit") int limit);
}
