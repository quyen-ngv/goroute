package com.ds.goroute.service;

import com.ds.goroute.type.ModeratedContentType;

import java.util.UUID;

/**
 * The second, context-aware filter layer (MOD-03, layer 2).
 *
 * <p>A term list catches the obvious cases and understands nothing. This layer reads the
 * sentence. It costs money and time, so it runs only on public content, only after the
 * content is already stored, and it never blocks: an outage of an external model must not
 * turn into an outage of publishing.
 */
public interface AiTextModerationService {

    /**
     * Queues one field for context-aware review. Returns immediately; a violation becomes
     * a flag in the shared queue rather than a rejection the author sees.
     */
    void reviewLater(ModeratedContentType contentType,
                     UUID contentId,
                     UUID ownerId,
                     String fieldLabel,
                     String text);
}
