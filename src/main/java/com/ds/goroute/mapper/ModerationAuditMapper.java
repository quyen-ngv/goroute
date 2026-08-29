package com.ds.goroute.mapper;

import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.entity.ModerationDecision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Write-side of the moderation record and the read-side of MOD-08.
 *
 * <p>These numbers cannot be reconstructed after the fact, which is why the decision row
 * is written at the moment the filter runs rather than derived later.
 */
@Mapper
public interface ModerationAuditMapper {

    int insertDecision(ModerationDecision decision);

    int insertImageResult(ImageModerationResult result);

    /** Totals per decision for the selected window. */
    List<Map<String, Object>> summarizeDecisions(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /** Queue throughput: how many flags arrived, how many are open, how long they wait. */
    Map<String, Object> summarizeQueue(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /**
     * Terms ranked by how often a human kept the content they flagged. This is the
     * false-positive list operations acts on: downgrade the term or add an exemption.
     */
    List<Map<String, Object>> rankFalsePositiveTerms(@Param("from") LocalDateTime from,
                                                     @Param("limit") int limit);

    /**
     * Content that the filter let through and users then reported: the miss rate.
     */
    Map<String, Object> summarizeMisses(@Param("from") LocalDateTime from);

    /** Image categories ranked by kept-after-flag, the image equivalent of the above. */
    List<Map<String, Object>> rankImageCategories(@Param("from") LocalDateTime from);
}
