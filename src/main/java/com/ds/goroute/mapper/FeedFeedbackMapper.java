package com.ds.goroute.mapper;

import com.ds.goroute.entity.FeedFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** feed_feedback (V245): posts and people a viewer hid from their feed. */
@Mapper
public interface FeedFeedbackMapper {

    int insert(FeedFeedback feedback);

    /** Undo: only the owner's own, still active row. Returns 0 when there is none. */
    int revoke(@Param("id") UUID id, @Param("userId") UUID userId);

    /** The viewer's hides still in force: every HIDE_POST, and HIDE_AUTHOR not yet expired. */
    List<FeedFeedback> findActive(@Param("userId") UUID userId);

    /** The active row hiding this exact target, if one exists, so hiding twice keeps one row. */
    FeedFeedback findActiveSame(FeedFeedback feedback);

    /** Expired author hides and long-revoked rows; the night job removes them in slices. */
    int deleteFinishedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
