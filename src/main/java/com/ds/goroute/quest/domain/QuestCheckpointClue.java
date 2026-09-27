package com.ds.goroute.quest.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One tier of an AREA checkpoint's finding-clue ladder (§3.14.1). Bought in tier order for
 * {@code costStars} (0 = free); {@code text}/{@code imageUrl} only leave the server once bought.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestCheckpointClue {

    private UUID id;
    private UUID checkpointId;
    private Integer tier;
    private String kind;
    private String text;
    private String imageUrl;
    private Integer costStars;
    private LocalDateTime createdAt;
}
