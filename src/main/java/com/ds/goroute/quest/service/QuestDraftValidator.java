package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestArObject;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCheckpointClue;
import com.ds.goroute.quest.domain.QuestCheckpointStop;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.QuestArAnchorMode;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestClueKind;
import com.ds.goroute.type.QuestCompletionMode;
import com.ds.goroute.type.QuestQuestionType;
import com.ds.goroute.utils.GeoDistance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The machine-check a quest must pass before it can be submitted (§3.3 layer 1, §3.12, §7.3).
 * Enforces the structural rules a reviewer should never have to catch by hand:
 *
 * <ul>
 *   <li>every TASK checkpoint has a job to do — a required question or a required check-in (D21);</li>
 *   <li>an ARRIVE checkpoint has something to take in, a STOPS one a reachable target, an AREA one
 *       a search circle and a well-formed clue ladder (§3.14), an AR_OBJECT one its object (§3.15);</li>
 *   <li>questions are well-formed for their type;</li>
 *   <li>counts stay inside the configured ceilings, and the price inside PRICE_MAX_STARS.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class QuestDraftValidator {

    /** §3.14.4: the AREA search circle, and a storytelling point's listening radius. */
    static final int MIN_SEARCH_RADIUS_M = 50;
    static final int MAX_SEARCH_RADIUS_M = 1000;
    static final int MIN_STOP_RADIUS_M = 10;
    static final int MAX_STOP_RADIUS_M = 100;
    static final int MAX_CLUES = 3;

    private final BusinessConfigService config;
    private final MarketplaceJson json;

    public void validateForSubmit(QuestVersion version) {
        List<String> errors = new ArrayList<>();

        if (isBlank(version.getTitle())) {
            errors.add("A title is required");
        }
        int price = version.getPriceStars() == null ? 0 : version.getPriceStars();
        int maxPrice = config.getInt(BusinessConfigKey.QUEST_PRICE_MAX_STARS);
        if (price < 0 || price > maxPrice) {
            errors.add("Price must be between 0 and " + maxPrice + " Stars");
        }
        if (version.getRewardStars() != null && version.getRewardStars() < 0) {
            errors.add("Reward Stars cannot be negative");
        }

        List<QuestCheckpoint> checkpoints = version.getCheckpoints();
        int min = config.getInt(BusinessConfigKey.QUEST_MIN_CHECKPOINTS);
        int max = config.getInt(BusinessConfigKey.QUEST_MAX_CHECKPOINTS);
        if (checkpoints.size() < min || checkpoints.size() > max) {
            errors.add("A quest needs between " + min + " and " + max + " checkpoints");
        }

        int maxQuestions = config.getInt(BusinessConfigKey.QUEST_MAX_QUESTIONS_PER_CHECKPOINT);
        int maxBonus = config.getInt(BusinessConfigKey.QUEST_MAX_BONUS_QUESTIONS);
        int minOptions = config.getInt(BusinessConfigKey.QUEST_CHOICE_MIN_OPTIONS);
        int maxOptions = config.getInt(BusinessConfigKey.QUEST_CHOICE_MAX_OPTIONS);

        DynamicLimits dynamic = new DynamicLimits(
                config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS),
                config.getInt(BusinessConfigKey.QUEST_CLUE_MAX_STARS),
                config.getInt(BusinessConfigKey.QUEST_MAX_STOPS_PER_CHECKPOINT),
                config.getInt(BusinessConfigKey.QUEST_STOP_MAX_DISTANCE_M),
                config.getInt(BusinessConfigKey.QUEST_AUDIO_MAX_SECONDS));
        for (int i = 0; i < checkpoints.size(); i++) {
            validateCheckpoint(checkpoints.get(i), i + 1, maxQuestions, maxBonus, minOptions, maxOptions, errors);
            validateDynamic(checkpoints.get(i), "Checkpoint " + (i + 1), dynamic, errors);
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, String.join("; ", errors));
        }
    }

    private void validateCheckpoint(QuestCheckpoint cp, int position, int maxQuestions, int maxBonus,
                                    int minOptions, int maxOptions, List<String> errors) {
        String where = "Checkpoint " + position;
        if (cp.getLatitude() == null || cp.getLongitude() == null) {
            errors.add(where + " has no coordinates");
        }
        // Where the coordinates came from (GPS on site or a map pin) is recorded for the reviewer
        // but no longer gates submission (§7.3, 2026-09-27).

        List<QuestQuestion> questions = cp.getQuestions();
        long requiredNonBonus = questions.stream().filter(q -> q.isRequired() && !q.isBonus()).count();
        long bonus = questions.stream().filter(QuestQuestion::isBonus).count();
        if (questions.size() > maxQuestions) {
            errors.add(where + " has more than " + maxQuestions + " questions");
        }
        if (bonus > maxBonus) {
            errors.add(where + " has more than " + maxBonus + " bonus questions");
        }
        // D21: a required question OR a required check-in — at least one. Only a TASK checkpoint;
        // ARRIVE and STOPS have their own rules (§3.14, validateDynamic).
        if (cp.completion() == QuestCompletionMode.TASK && requiredNonBonus == 0 && !cp.isRequiresCheckin()) {
            errors.add(where + " needs a required question or a required check-in");
        }
        for (QuestQuestion q : questions) {
            validateQuestion(q, where, minOptions, maxOptions, errors);
        }
    }

    /** Ceilings for §3.14: clue prices, storytelling points and recordings. */
    private record DynamicLimits(int defaultUnlockRadius, int clueMaxStars, int maxStops,
                                 int stopMaxDistanceM, int audioMaxSeconds) {
    }

    /**
     * §3.14.4: the find mode (AREA search circle and clue ladder), the completion mode (ARRIVE has
     * something to take in and nothing to do; STOPS asks for a reachable number of points), the
     * storytelling points themselves, and every recording's length.
     */
    private void validateDynamic(QuestCheckpoint cp, String where, DynamicLimits limits, List<String> errors) {
        if (cp.isArea()) {
            int unlock = cp.getRadiusM() != null ? cp.getRadiusM() : limits.defaultUnlockRadius();
            Integer search = cp.getSearchRadiusM();
            if (search == null || search < MIN_SEARCH_RADIUS_M || search > MAX_SEARCH_RADIUS_M) {
                errors.add(where + " needs a search area of " + MIN_SEARCH_RADIUS_M + "–" + MAX_SEARCH_RADIUS_M + " m");
            } else if (search <= unlock) {
                errors.add(where + " needs a search area larger than its unlock radius");
            }
            validateClues(cp.getClues(), where, limits.clueMaxStars(), errors);
        }

        long required = cp.getQuestions().stream().filter(q -> q.isRequired() && !q.isBonus()).count();
        List<QuestCheckpointStop> stops = cp.getStops();
        switch (cp.completion()) {
            case ARRIVE -> {
                if (required > 0 || cp.isRequiresCheckin()) {
                    errors.add(where + " is guide-only: it cannot have a required question or check-in");
                }
                boolean hasContent = !isBlank(cp.getStory()) || !isBlank(cp.getStoryAudioUrl()) || !stops.isEmpty();
                if (!hasContent) {
                    errors.add(where + " is guide-only and needs a story, a recording or a storytelling point");
                }
            }
            case STOPS -> {
                Integer min = cp.getMinStops();
                if (min == null || min < 1 || min > stops.size()) {
                    errors.add(where + " must ask for 1 to " + stops.size() + " storytelling points");
                }
            }
            case AR_OBJECT -> validateArObject(cp, where, limits, errors);
            case TASK -> {
                // D21, checked in validateCheckpoint.
            }
        }

        if (stops.size() > limits.maxStops()) {
            errors.add(where + " has more than " + limits.maxStops() + " storytelling points");
        }
        for (int k = 0; k < stops.size(); k++) {
            validateStop(cp, stops.get(k), where + " storytelling point " + (k + 1), limits, errors);
        }
        if (cp.getStoryAudioSeconds() != null && cp.getStoryAudioSeconds() > limits.audioMaxSeconds()) {
            errors.add(where + " has a story recording longer than " + limits.audioMaxSeconds() + " seconds");
        }
    }

    /**
     * §3.15: an AR_OBJECT checkpoint needs its object — an asset, where it stands, and for an IMAGE
     * anchor at least one landmark. It is found by walking to it, so it is a PIN checkpoint, and it
     * must stand within reach of the checkpoint (the same limit as a storytelling point).
     */
    private void validateArObject(QuestCheckpoint cp, String where, DynamicLimits limits, List<String> errors) {
        if (cp.isArea()) {
            errors.add(where + " finds an AR object, so its location cannot be hidden in a search area");
        }
        QuestArObject object = cp.getArObject() == null ? null : json.read(cp.getArObject(), QuestArObject.class, null);
        if (object == null) {
            errors.add(where + " needs its AR object");
            return;
        }
        if (object.assetId() == null) {
            errors.add(where + " needs a 3D object chosen from the library");
        }
        if (object.latitude() == null || object.longitude() == null) {
            errors.add(where + " needs a place for its AR object");
        } else {
            Double distance = GeoDistance.betweenOrNull(cp.getLatitude(), cp.getLongitude(),
                    object.latitude(), object.longitude());
            if (distance != null && distance > limits.stopMaxDistanceM()) {
                errors.add(where + ": its AR object is more than " + limits.stopMaxDistanceM() + " m away");
            }
        }
        if (QuestArAnchorMode.IMAGE.name().equals(object.anchorMode())
                && object.markersOrEmpty().stream().noneMatch(QuestArObject.Marker::placed)) {
            errors.add(where + " anchors its AR object to a landmark but has not placed it at one");
        }
        if (object.audioSeconds() != null && object.audioSeconds() > limits.audioMaxSeconds()) {
            errors.add(where + " has an AR object recording longer than " + limits.audioMaxSeconds() + " seconds");
        }
    }

    private void validateClues(List<QuestCheckpointClue> clues, String where, int clueMaxStars, List<String> errors) {
        if (clues.size() > MAX_CLUES) {
            errors.add(where + " has more than " + MAX_CLUES + " clues");
        }
        for (int t = 0; t < clues.size(); t++) {
            QuestCheckpointClue clue = clues.get(t);
            String at = where + " clue " + (t + 1);
            if (clue.getTier() == null || clue.getTier() != t + 1) {
                errors.add(at + " is out of order");
            }
            int cost = clue.getCostStars() == null ? 0 : clue.getCostStars();
            if (cost < 0 || cost > clueMaxStars) {
                errors.add(at + " must cost 0 to " + clueMaxStars + " Stars");
            }
            QuestClueKind kind;
            try {
                kind = QuestClueKind.valueOf(clue.getKind());
            } catch (RuntimeException ex) {
                errors.add(at + " has an unknown kind");
                continue;
            }
            switch (kind) {
                case TEXT -> {
                    if (isBlank(clue.getText())) {
                        errors.add(at + " needs its text");
                    }
                }
                case PHOTO -> {
                    if (isBlank(clue.getImageUrl())) {
                        errors.add(at + " needs its photo");
                    }
                }
                case REVEAL -> {
                    if (t != clues.size() - 1) {
                        errors.add(at + ": the pin reveal must be the last clue");
                    }
                }
            }
        }
    }

    private void validateStop(QuestCheckpoint cp, QuestCheckpointStop stop, String where, DynamicLimits limits,
                              List<String> errors) {
        if (isBlank(stop.getName())) {
            errors.add(where + " needs a name");
        }
        if (stop.getLatitude() == null || stop.getLongitude() == null) {
            errors.add(where + " has no location");
            return;
        }
        int radius = stop.getRadiusM() == null ? 0 : stop.getRadiusM();
        if (radius < MIN_STOP_RADIUS_M || radius > MAX_STOP_RADIUS_M) {
            errors.add(where + " needs a radius of " + MIN_STOP_RADIUS_M + "–" + MAX_STOP_RADIUS_M + " m");
        }
        Double distance = GeoDistance.betweenOrNull(cp.getLatitude(), cp.getLongitude(),
                stop.getLatitude(), stop.getLongitude());
        if (distance != null && distance > limits.stopMaxDistanceM()) {
            errors.add(where + " is more than " + limits.stopMaxDistanceM() + " m from its checkpoint");
        }
        if (stop.getAudioSeconds() != null && stop.getAudioSeconds() > limits.audioMaxSeconds()) {
            errors.add(where + " has a recording longer than " + limits.audioMaxSeconds() + " seconds");
        }
    }

    private void validateQuestion(QuestQuestion q, String where, int minOptions, int maxOptions, List<String> errors) {
        QuestQuestionType type;
        try {
            type = QuestQuestionType.valueOf(q.getType());
        } catch (RuntimeException ex) {
            errors.add(where + " has a question of unknown type");
            return;
        }
        if (isBlank(q.getPrompt())) {
            errors.add(where + " has a question with no prompt");
        }
        switch (type) {
            case TEXT -> {
                if (isBlank(q.getAnswerPlain())) {
                    errors.add(where + " has a text question with no answer");
                }
            }
            case NUMBER -> {
                if (isBlank(q.getAnswerPlain()) || !isNumeric(q.getAnswerPlain())) {
                    errors.add(where + " has a number question whose answer is not a number");
                }
            }
            case CHOICE, MULTI_CHOICE -> {
                int options = q.getChoices() == null ? 0 : q.getChoices().size();
                long correct = q.getChoices() == null ? 0
                        : q.getChoices().stream().filter(c -> c.isCorrect()).count();
                if (options < minOptions || options > maxOptions) {
                    errors.add(where + " has a choice question with " + options + " options (need "
                            + minOptions + "–" + maxOptions + ")");
                }
                if (correct == 0) {
                    errors.add(where + " has a choice question with no correct answer");
                }
                if (type == QuestQuestionType.CHOICE && correct > 1) {
                    errors.add(where + " has a single-choice question with more than one correct answer");
                }
            }
            case PHOTO -> {
                // Always scored correct (D12); nothing to validate beyond the prompt.
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
