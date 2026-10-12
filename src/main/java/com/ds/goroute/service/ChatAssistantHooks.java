package com.ds.goroute.service;

import com.ds.goroute.dto.response.AssistantInfoResponse;
import com.ds.goroute.entity.MarketplaceConversation;

import java.util.UUID;

/**
 * What the chat needs to know about the assistant without depending on how it works: the
 * assistant's own services depend on the chat to post their answers, so the chat only sees
 * this narrow view.
 */
public interface ChatAssistantHooks {

    /** Pengoo as seen by this viewer in this conversation, or null where it does not take part. */
    AssistantInfoResponse info(UUID viewerId, MarketplaceConversation conversation);
}
