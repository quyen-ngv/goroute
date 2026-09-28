package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A run as the player sees it (§3.8, §3.14). Run-scoped and deliberately narrow: only the current
 * checkpoint carries coordinates and questions (an AREA checkpoint's only after its REVEAL clue is
 * bought; before that, only the offset search circle), cleared checkpoints carry their unlocked story,
 * and checkpoints still ahead carry only their public facts ({@link UpcomingCheckpointView}) — never
 * their photos, questions or story, and their place only when the creator shows the whole route. No question here carries an
 * answer, a correct-choice flag, or unbought hint text (rules 5.2/5.3). The offline buffer takes
 * only {@link #current}, one checkpoint ahead.
 */
public record QuestRunResponse(
        UUID runId,
        UUID questId,
        String status,
        int clearedCheckpoints,
        int totalCheckpoints,
        boolean completed,
        List<ClearedCheckpointView> cleared,
        CurrentCheckpointView current,
        int arrivalClientIntervalSeconds,
        /** The version's content language: what the story is written and recorded in (§3.14.3). */
        String language,
        /** The checkpoints after the current one, in order: public facts only, so the player sees the route. */
        List<UpcomingCheckpointView> upcoming,
        /** The creator shows the whole route: every upcoming checkpoint is placed on the map. */
        boolean revealRoute) {

    /**
     * What a checkpoint holds, told before the player gets there: how many questions, whether a
     * check-in is needed, how many storytelling points, whether a story (and a recording) waits.
     * Counts and flags only — never the prompts, the story or where anything is.
     */
    public record CheckpointPreview(int questionCount, int requiredQuestionCount, boolean requiresCheckin,
                                    int stopCount, boolean hasStory, boolean hasStoryAudio,
                                    Integer storyAudioSeconds) {
    }

    /**
     * A checkpoint still ahead: its name, kind and what waits there — never photos or content. Placed
     * on the map only when the creator shows the whole route: a PIN at its spot, an AREA only by its
     * offset search circle.
     */
    public record UpcomingCheckpointView(UUID checkpointId, int sortOrder, String name, String category,
                                         String findMode, String completionMode, Integer minStops,
                                         CheckpointPreview preview,
                                         BigDecimal latitude, BigDecimal longitude, Integer radiusM,
                                         SearchAreaView searchArea) {
    }

    public record ClearedCheckpointView(UUID checkpointId, int sortOrder, String name, String category,
                                        String story, List<String> imageUrls,
                                        /** Kept so a cleared checkpoint can be heard again. */
                                        String storyAudioUrl, Integer storyAudioSeconds,
                                        List<StopView> stops,
                                        /** No secret once cleared: the journey map shows it. */
                                        BigDecimal latitude, BigDecimal longitude,
                                        String completionMode) {
    }

    public record CurrentCheckpointView(
            UUID checkpointId,
            int sortOrder,
            String name,
            String category,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusM,
            boolean requiresCheckin,
            boolean checkinDone,
            boolean unlocked,
            int stableStreak,
            /** The creator's photos of the spot, shown before arrival so the player can find it. */
            List<String> imageUrls,
            /**
             * PIN: after arrival. AREA: the task is shown from the start (the prompt tells the player
             * what to look for), but an answer is only accepted once unlocked.
             */
            List<RunQuestionView> questions,
            String findMode,
            /** AREA only: where to search. Its centre is offset from the real spot. */
            SearchAreaView searchArea,
            boolean hotColdEnabled,
            String completionMode,
            Integer minStops,
            /** Only once unlocked. */
            String story,
            String storyAudioUrl,
            Integer storyAudioSeconds,
            /** The finding-clue ladder; a clue's text and photo only once bought. */
            List<ClueView> clues,
            /** Storytelling points, only once unlocked. */
            List<StopView> stops,
            /** Storytelling points heard by GPS inside their radius: what STOPS counts. */
            int stopsCounted,
            /** True once the REVEAL clue was bought. */
            boolean assisted,
            /** What waits here, shown before arrival. */
            CheckpointPreview preview,
            /** AR_OBJECT only (§3.15), and only for an app that can show AR; null otherwise. */
            QuestArObjectView arObject,
            /** True once this member tapped the AR object. */
            boolean arTapped) {
    }

    public record SearchAreaView(BigDecimal latitude, BigDecimal longitude, int radiusM) {
    }

    public record ClueView(int tier, String kind, int costStars, boolean bought, String text, String imageUrl) {
    }

    public record StopView(
            UUID stopId,
            int sortOrder,
            String name,
            String category,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusM,
            String story,
            List<String> imageUrls,
            String audioUrl,
            Integer audioSeconds,
            boolean visited,
            boolean countsTowardCompletion) {
    }

    public record RunQuestionView(
            UUID questionId,
            int sortOrder,
            boolean required,
            boolean bonus,
            String type,
            String prompt,
            UUID imageMediaId,
            int guessCount,
            boolean answeredCorrectly,
            boolean skipped,
            /** Hint text only for tiers the player has bought; null otherwise. */
            String boughtHint,
            /** Choices in shuffled order, with stable ids and NO correctness flag. */
            List<RunChoiceView> choices) {
    }

    public record RunChoiceView(UUID id, String content) {
    }
}
