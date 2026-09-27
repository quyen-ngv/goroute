package com.ds.goroute.quest.domain;

import com.ds.goroute.type.QuestQuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A question at a checkpoint (§3.12). A checkpoint has 0..N of these; the gate to advance is that
 * every required, non-bonus question is answered correctly (and the required check-in, if any, is
 * done). {@code answerPlain}, {@code answerVariants} and the hint tiers are server-only and must
 * never appear in a public DTO (§6.2.2, D6).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestQuestion {

    private UUID id;
    private UUID checkpointId;
    private Integer sortOrder;
    private boolean required;
    private boolean bonus;
    private String type;
    private String prompt;
    private UUID imageMediaId;
    private String answerPlain;
    /** JSON array of accepted answer variants. */
    private String answerVariants;
    private BigDecimal numberTolerance;
    private String hintTier1;
    private String hintTier2;
    private String hintTier3;
    private Integer bonusStars;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<QuestQuestionChoice> choices = new ArrayList<>();

    public QuestQuestionType questionType() {
        return QuestQuestionType.valueOf(type);
    }
}
