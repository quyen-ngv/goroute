package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestCaptureSource;
import com.ds.goroute.type.QuestQuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The machine-check a quest must pass before it can be submitted (§3.3 layer 1, §3.12, §7.3).
 * Enforces the structural rules a reviewer should never have to catch by hand:
 *
 * <ul>
 *   <li>every checkpoint has a job to do — a required question or a required check-in (D21);</li>
 *   <li>every checkpoint's coordinates were recorded in the field, not pinned on a map (§7.3);</li>
 *   <li>questions are well-formed for their type;</li>
 *   <li>counts stay inside the configured ceilings, and the price inside PRICE_MAX_STARS.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class QuestDraftValidator {

    private final BusinessConfigService config;

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

        for (int i = 0; i < checkpoints.size(); i++) {
            validateCheckpoint(checkpoints.get(i), i + 1, maxQuestions, maxBonus, minOptions, maxOptions, errors);
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
        // §7.3: coordinates must have been recorded on the spot.
        if (cp.getCaptureSource() == null || QuestCaptureSource.valueOf(cp.getCaptureSource()) != QuestCaptureSource.FIELD) {
            errors.add(where + " must be captured in the field before it can be submitted");
        }

        List<QuestQuestion> questions = cp.getQuestions();
        long requiredNonBonus = questions.stream().filter(q -> q.isRequired() && !q.isBonus()).count();
        long bonus = questions.stream().filter(QuestQuestion::isBonus).count();
        if (questions.size() > maxQuestions) {
            errors.add(where + " has more than " + maxQuestions + " questions");
        }
        if (bonus > maxBonus) {
            errors.add(where + " has more than " + maxBonus + " bonus questions");
        }
        // D21: a required question OR a required check-in — at least one.
        if (requiredNonBonus == 0 && !cp.isRequiresCheckin()) {
            errors.add(where + " needs a required question or a required check-in");
        }
        for (QuestQuestion q : questions) {
            validateQuestion(q, where, minOptions, maxOptions, errors);
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
