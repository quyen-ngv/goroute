package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpsertMessageTemplateRequest;
import com.ds.goroute.dto.response.MessageTemplateResponse;

import java.util.List;
import java.util.UUID;

/** Quick replies a partner keeps for the marketplace inbox; scoped to one organization, CHAT_WRITE required. */
public interface MarketplaceMessageTemplateService {
    List<MessageTemplateResponse> list(UUID actorUserId, UUID organizationId);
    MessageTemplateResponse create(UUID actorUserId, UUID organizationId, UpsertMessageTemplateRequest request);
    MessageTemplateResponse update(UUID actorUserId, UUID organizationId, UUID templateId, UpsertMessageTemplateRequest request);
    void delete(UUID actorUserId, UUID organizationId, UUID templateId);
}
