package com.ds.goroute.quest.dto;

import java.util.List;
import java.util.UUID;

/**
 * A discovery card. Never carries checkpoint coordinates, answers, hints or location keys — the
 * four things §7.3 (rules 2 and 3) forbid in any public shape. Enforced by {@code QuestPublicDtoLeakTest}.
 */
public record QuestPublicSummaryResponse(
        UUID id,
        String origin,
        String title,
        String summary,
        UUID coverMediaId,
        Integer difficulty,
        Integer estimatedMinutes,
        Integer distanceMeters,
        int priceStars,
        int rewardStars,
        String provinceCode,
        String wardCode,
        int checkpointCount,
        List<String> amenityTags) {
}
