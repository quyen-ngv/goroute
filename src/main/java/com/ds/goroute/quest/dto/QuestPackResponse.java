package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A whole quest version, answers included, for playing on the phone (local-first play). Served
 * only to a player entitled to play it (a free quest, an unlocked one, or the creator's own).
 *
 * <p>Unlike every public shape this one deliberately carries coordinates, answers, hints and
 * correct-choice flags: the phone grades and checks arrival itself so a run works with no network.
 * Nothing is taken on trust for rewards — the finished run is uploaded and replayed by
 * {@code QuestPlayService.syncLocalRun}, which re-grades and re-checks everything.
 */
public record QuestPackResponse(
        UUID questId,
        UUID versionId,
        int version,
        int contentRevision,
        String title,
        String summary,
        String coverUrl,
        String language,
        int priceStars,
        int rewardStars,
        Settings settings,
        List<Checkpoint> checkpoints) {

    /** The server's play rules, so the phone decides exactly as the server would. */
    public record Settings(int arrivalStableSamples, int arrivalClientIntervalSeconds, int defaultUnlockRadiusM,
                           int maxAccuracyM, int maxGuesses, int proximityMinIntervalSeconds,
                           /** §3.15: how near an AR object a tap counts. */
                           int arInteractRadiusM) {
    }

    public record Checkpoint(
            UUID checkpointId,
            int sortOrder,
            String name,
            String category,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusM,
            boolean requiresCheckin,
            List<String> imageUrls,
            String findMode,
            BigDecimal searchCenterLat,
            BigDecimal searchCenterLng,
            Integer searchRadiusM,
            boolean hotColdEnabled,
            String completionMode,
            Integer minStops,
            String story,
            String storyAudioUrl,
            Integer storyAudioSeconds,
            List<Clue> clues,
            List<Stop> stops,
            List<Question> questions,
            /**
             * AR_OBJECT only (§3.15). An app that did not say it can show AR gets the checkpoint as
             * ARRIVE and no object, so an older app plays it instead of getting stuck.
             */
            QuestArObjectView arObject) {
    }

    public record Clue(int tier, String kind, int costStars, String text, String imageUrl) {
    }

    public record Stop(UUID stopId, int sortOrder, String name, String category, BigDecimal latitude,
                       BigDecimal longitude, int radiusM, String story, List<String> imageUrls, String audioUrl,
                       Integer audioSeconds) {
    }

    public record Question(
            UUID questionId,
            int sortOrder,
            boolean required,
            boolean bonus,
            String type,
            String prompt,
            UUID imageMediaId,
            String answerPlain,
            List<String> answerVariants,
            BigDecimal numberTolerance,
            String hintTier1,
            String hintTier2,
            String hintTier3,
            List<Choice> choices) {
    }

    public record Choice(UUID id, int sortOrder, String content, boolean correct) {
    }
}
