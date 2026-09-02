package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpsertMessageTemplateRequest;
import com.ds.goroute.dto.response.MessageTemplateResponse;
import com.ds.goroute.entity.MarketplaceMessageTemplate;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceMessageTemplateRepository;
import com.ds.goroute.service.MarketplaceMessageTemplateService;
import com.ds.goroute.service.PartnerAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceMessageTemplateServiceImpl implements MarketplaceMessageTemplateService {
    /** Hard ceiling per organization; the inbox picker is a short list, not a knowledge base. */
    static final int MAX_TEMPLATES_PER_ORGANIZATION = 50;
    private static final String PERMISSION = "CHAT_WRITE";

    private final MarketplaceMessageTemplateRepository repository;
    private final PartnerAuthorizationService authorization;

    @Override
    @Transactional(readOnly = true)
    public List<MessageTemplateResponse> list(UUID actorUserId, UUID organizationId) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        return repository.findByOrganization(organizationId, MAX_TEMPLATES_PER_ORGANIZATION)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public MessageTemplateResponse create(UUID actorUserId, UUID organizationId, UpsertMessageTemplateRequest request) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        if (repository.countByOrganization(organizationId) >= MAX_TEMPLATES_PER_ORGANIZATION) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "An organization can keep at most " + MAX_TEMPLATES_PER_ORGANIZATION + " quick replies");
        }
        LocalDateTime now = LocalDateTime.now();
        MarketplaceMessageTemplate template = MarketplaceMessageTemplate.builder()
                .id(UUID.randomUUID()).organizationId(organizationId).title(request.getTitle().trim())
                .body(request.getBody().trim()).sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .createdBy(actorUserId).updatedBy(actorUserId).createdAt(now).updatedAt(now).build();
        repository.insert(template);
        return toResponse(template);
    }

    @Override
    @Transactional
    public MessageTemplateResponse update(UUID actorUserId, UUID organizationId, UUID templateId,
                                          UpsertMessageTemplateRequest request) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        MarketplaceMessageTemplate template = required(templateId, organizationId);
        template.setTitle(request.getTitle().trim());
        template.setBody(request.getBody().trim());
        if (request.getSortOrder() != null) template.setSortOrder(request.getSortOrder());
        template.setUpdatedBy(actorUserId);
        template.setUpdatedAt(LocalDateTime.now());
        if (repository.update(template) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quick reply not found");
        }
        return toResponse(template);
    }

    @Override
    @Transactional
    public void delete(UUID actorUserId, UUID organizationId, UUID templateId) {
        authorization.requirePermission(organizationId, actorUserId, PERMISSION);
        required(templateId, organizationId);
        repository.delete(templateId, organizationId);
    }

    private MarketplaceMessageTemplate required(UUID templateId, UUID organizationId) {
        return repository.findById(templateId, organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quick reply not found"));
    }

    private MessageTemplateResponse toResponse(MarketplaceMessageTemplate value) {
        return MessageTemplateResponse.builder().id(value.getId()).organizationId(value.getOrganizationId())
                .title(value.getTitle()).body(value.getBody()).sortOrder(value.getSortOrder()).build();
    }
}
