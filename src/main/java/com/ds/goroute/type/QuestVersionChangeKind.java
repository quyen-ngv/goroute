package com.ds.goroute.type;

/**
 * How a {@code quest_versions} row differs from the one before it (§6.2.1). Both kinds mint a new
 * immutable version; they differ only in the review path:
 *
 * <ul>
 *   <li>{@code MATERIAL} &mdash; bumps {@code version}; the quest goes back through review and
 *       {@code published_version_id} stays on the old row until a reviewer approves.</li>
 *   <li>{@code MINOR} &mdash; bumps {@code content_revision}; {@code published_version_id} moves
 *       to the new row immediately and the quest stays PUBLISHED with the post-review flag set.</li>
 * </ul>
 */
public enum QuestVersionChangeKind {
    MATERIAL,
    MINOR
}
