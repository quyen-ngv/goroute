package com.ds.goroute.type;

/**
 * Where a library image came from, kept with the asset so the team can tell which images are
 * licensed (an Iconscout subscription, MIT-licensed Fluent Emoji) and which still need checking.
 */
public enum AssetSource {
    ICONSCOUT,
    FLUENT_EMOJI,
    AI_GENERATED,
    IN_HOUSE,
    PARTNER,
    UNKNOWN
}
