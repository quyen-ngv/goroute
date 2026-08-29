package com.ds.goroute.service.moderation;

import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationCategory;

import java.util.UUID;

/**
 * The outcome of running one piece of text or one image through the filter.
 *
 * <p>Deliberately not a boolean: the policy has three outcomes, and the caller needs the
 * violated category to build an error message the user can act on (MOD-01, rule 4).
 *
 * @param action       what the system does
 * @param category     which policy group was matched, {@code null} when allowed
 * @param matchedTermId the term list entry that fired, {@code null} for the AI and image
 *                      layers; carried so MOD-08 can rank the terms causing false blocks
 * @param matchedText  the offending fragment, for the review queue
 */
public record ModerationVerdict(
        ModerationAction action,
        ModerationCategory category,
        UUID matchedTermId,
        String matchedText) {

    private static final ModerationVerdict ALLOWED =
            new ModerationVerdict(ModerationAction.ALLOW, null, null, null);

    public static ModerationVerdict allowed() {
        return ALLOWED;
    }

    public static ModerationVerdict of(ModerationAction action, ModerationCategory category,
                                       UUID matchedTermId, String matchedText) {
        return action == ModerationAction.ALLOW
                ? ALLOWED
                : new ModerationVerdict(action, category, matchedTermId, matchedText);
    }

    public boolean blocks() {
        return action.blocks();
    }

    public boolean isAllowed() {
        return action == ModerationAction.ALLOW;
    }

    /** Keeps the strongest of two verdicts, used to merge per-field results. */
    public ModerationVerdict merge(ModerationVerdict other) {
        if (other == null || other.isAllowed()) {
            return this;
        }
        return other.action.weight() > action.weight() ? other : this;
    }

    /**
     * Applies the visibility downgrade from MOD-07: outside fully strict tiers a block
     * becomes a flag, except for the severe groups where letting content through even
     * briefly is real damage.
     */
    public ModerationVerdict downgradeUnlessSevere() {
        if (action != ModerationAction.BLOCK || (category != null && category.isSevere())) {
            return this;
        }
        return new ModerationVerdict(ModerationAction.FLAG, category, matchedTermId, matchedText);
    }
}
