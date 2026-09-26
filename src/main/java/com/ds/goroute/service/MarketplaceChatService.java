package com.ds.goroute.service;

import com.ds.goroute.dto.request.EditMarketplaceMessageRequest;
import com.ds.goroute.dto.request.MuteConversationRequest;
import com.ds.goroute.dto.request.SendMarketplaceMessageRequest;
import com.ds.goroute.dto.request.StartMarketplaceConversationRequest;
import com.ds.goroute.dto.request.UpdateConversationStatusRequest;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Every conversation in the product: trip groups, person-to-person threads, and the ones
 * attached to a booking or an order.
 *
 * <p>One service rather than one per kind. The difference between them is who may read a
 * thread and who may be told about it, which is a handful of branches; duplicating sending,
 * paging, read markers and realtime for each kind would be the real cost.
 */
public interface MarketplaceChatService {

    MarketplaceConversationResponse start(UUID actor, StartMarketplaceConversationRequest request);

    /** The inbox. {@code types} filters the chips; empty means everything. */
    List<MarketplaceConversationResponse> listMine(UUID actor, List<String> types, int page, int size);

    /** Threads with something unread in them, muted ones excluded: the tab badge. */
    long unreadConversationCount(UUID actor);

    MarketplaceConversationResponse get(UUID actor, UUID id);

    /** Messages after a sequence number; used by the catch-up poll. */
    List<MarketplaceMessageResponse> messages(UUID actor, UUID id, Long after, int limit);

    /** The newest page, oldest-first; {@code before} walks back into older history. */
    List<MarketplaceMessageResponse> latestMessages(UUID actor, UUID id, Long before, int limit);

    List<MarketplaceMessageResponse> searchMessages(UUID actor, UUID id, String query, int limit);

    MarketplaceMessageResponse send(UUID actor, UUID id, SendMarketplaceMessageRequest request);

    void markRead(UUID actor, UUID id, UUID messageId);

    void deleteOwnMessage(UUID actor, UUID conversationId, UUID messageId);

    MarketplaceMessageResponse editMessage(UUID actor, UUID conversationId, UUID messageId,
                                           EditMarketplaceMessageRequest request);

    MarketplaceMessageResponse react(UUID actor, UUID conversationId, UUID messageId, String emoji, boolean add);

    MarketplaceConversationResponse mute(UUID actor, UUID conversationId, MuteConversationRequest request);

    /** Pins a message for everyone, or clears the pin with a null message id. */
    MarketplaceConversationResponse pin(UUID actor, UUID conversationId, UUID messageId);

    /**
     * A line the system wrote into a thread. Called by whatever owns the event -- a trip
     * gaining or losing a member -- not by a client.
     */
    MarketplaceMessageResponse postSystemMessage(UUID conversationId, String event,
                                                 UUID subjectUserId, String subjectName);

    List<MarketplaceConversationResponse> partnerList(UUID actor, UUID organizationId, String status,
                                                      int page, int size);

    MarketplaceConversationResponse partnerUpdate(UUID actor, UUID id, UpdateConversationStatusRequest request);

    List<MarketplaceConversationResponse> adminList(String query, List<String> status, List<String> conversationType,
                                                    String sort, boolean descending, int page, int size);

    List<MarketplaceMessageResponse> adminMessages(UUID actor, UUID id, Long after, int limit, String reason);

    MarketplaceConversationResponse adminUpdate(UUID actor, UUID id, UpdateConversationStatusRequest request);

    MarketplaceMessageResponse adminSend(UUID actor, UUID id, SendMarketplaceMessageRequest request);

    void adminRedact(UUID actor, UUID conversationId, UUID messageId, String reason);
}
