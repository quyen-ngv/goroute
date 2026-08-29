package com.ds.goroute.type;

/**
 * The short list a reporter picks from (SOC-06a). Kept short on purpose: a long list
 * makes people pick at random, which makes the queue useless.
 */
public enum ContentReportReason {
    SEXUAL_CONTENT(ModerationCategory.SEXUAL),
    VIOLENCE(ModerationCategory.VIOLENCE),
    HATE_OR_DISCRIMINATION(ModerationCategory.HATE_SPEECH),
    HARASSMENT(ModerationCategory.HARASSMENT),
    SPAM_OR_ADVERTISING(ModerationCategory.SPAM),
    SCAM(ModerationCategory.SCAM),
    PRIVACY_VIOLATION(ModerationCategory.PERSONAL_DATA),
    COPYRIGHT(ModerationCategory.COPYRIGHT),
    OTHER(ModerationCategory.HARASSMENT);

    private final ModerationCategory category;

    ContentReportReason(ModerationCategory category) {
        this.category = category;
    }

    public ModerationCategory category() {
        return category;
    }
}
