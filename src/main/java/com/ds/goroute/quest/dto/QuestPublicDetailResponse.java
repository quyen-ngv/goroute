package com.ds.goroute.quest.dto;

import java.util.List;
import java.util.UUID;

/**
 * The pre-purchase detail page. Like the summary, it carries no checkpoint coordinates, answers,
 * hints or location keys: coordinates are the thing that reconstructs the whole route, so even a
 * detail page a stranger can open shows only counts and high-level facts. During a run, the next
 * checkpoint's coordinates are served by a separate run-scoped shape (§3.8), one checkpoint at a
 * time. Enforced by {@code QuestPublicDtoLeakTest}.
 */
public record QuestPublicDetailResponse(
        UUID id,
        String origin,
        String title,
        String summary,
        String description,
        UUID coverMediaId,
        Integer difficulty,
        Integer estimatedMinutes,
        Integer distanceMeters,
        int priceStars,
        int rewardStars,
        String provinceCode,
        String wardCode,
        String safetyNotes,
        List<String> amenityTags,
        List<Integer> playableMonths,
        Integer runExpiryHours,
        int checkpointCount,
        int requiredCheckinCount,
        boolean creatorSeesPlayers) {
}
