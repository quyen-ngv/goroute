package com.ds.goroute.type;

/**
 * Restricted content groups defined by the content policy
 * ({@code .docs/policy/CONTENT_POLICY.md}, section 3).
 *
 * <p>The enum only names the groups. The action taken for a group is data, not code,
 * so operations can downgrade a group from BLOCK to FLAG without a release.
 */
public enum ModerationCategory {
    SEXUAL,
    VIOLENCE,
    HATE_SPEECH,
    HARASSMENT,
    POLITICAL,
    SPAM,
    SCAM,
    PERSONAL_DATA,
    COPYRIGHT,
    DRUGS_WEAPONS,
    HATE_SYMBOL;

    /**
     * Groups where letting content through even briefly is real damage. These keep their
     * strictness regardless of how visible the content is (policy section 2).
     */
    public boolean isSevere() {
        return this == SEXUAL || this == VIOLENCE || this == HATE_SYMBOL;
    }

    public FlagSeverity severity() {
        return switch (this) {
            case SEXUAL, VIOLENCE, HATE_SYMBOL, HATE_SPEECH, SCAM -> FlagSeverity.HIGH;
            case HARASSMENT, PERSONAL_DATA, DRUGS_WEAPONS, POLITICAL -> FlagSeverity.MEDIUM;
            case SPAM, COPYRIGHT -> FlagSeverity.LOW;
        };
    }

    /** Queue ordering is by seriousness, never by arrival time (MOD-06). */
    public int priority() {
        return switch (severity()) {
            case HIGH -> 100;
            case MEDIUM -> 50;
            case LOW -> 10;
        };
    }
}
