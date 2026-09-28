package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The pre-purchase detail page. It carries no answers, hints or location keys. Checkpoint
 * coordinates appear only in {@link #route}, and only when the creator chose to show the whole
 * route ({@link #revealRoute}, the default): then each checkpoint is placed on the map — a PIN
 * checkpoint at its spot, an AREA checkpoint only at its offset search circle, never its real spot.
 * A creator who keeps the route hidden gets an empty list, and the run serves the next checkpoint
 * one at a time (§3.8). Enforced by {@code QuestPublicDtoLeakTest}.
 */
public record QuestPublicDetailResponse(
        UUID id,
        String origin,
        String title,
        String summary,
        String description,
        UUID coverMediaId,
        String coverUrl,
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
        boolean creatorSeesPlayers,
        /** The one language the quest is written in (one quest, one language). */
        String language,
        /** The creator shows every checkpoint from the start. */
        boolean revealRoute,
        /** The checkpoints in order, on the map; empty unless {@link #revealRoute}. */
        List<RouteStop> route) {

    /**
     * One checkpoint of a shown route. PIN: its spot and unlock radius. AREA: the offset search
     * circle (the real spot stays hidden until the REVEAL clue is bought during a run).
     */
    public record RouteStop(int sortOrder, String name, String category, String findMode,
                            BigDecimal latitude, BigDecimal longitude, Integer radiusM) {
    }
}
