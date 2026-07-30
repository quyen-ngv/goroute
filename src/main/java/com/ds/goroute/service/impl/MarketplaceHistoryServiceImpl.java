package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.MarketplaceEntityVersionResponse;
import com.ds.goroute.entity.MarketplaceAuditEvent;
import com.ds.goroute.entity.MarketplaceEntityVersion;
import com.ds.goroute.repository.MarketplaceHistoryRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceHistoryServiceImpl implements MarketplaceHistoryService {
    private final MarketplaceHistoryRepository repository;
    private final ObjectMapper objectMapper;
    private final AdminMapper adminMapper;

    @Override
    @Transactional
    public void record(UUID organizationId, String entityType, UUID entityId, String action,
                       Object snapshot, List<String> changedFields, UUID actorUserId, String actorType, String reason) {
        LocalDateTime now = LocalDateTime.now();
        String effectiveActorType = actorUserId != null && adminMapper.hasAnyRole(actorUserId)
                ? "ADMIN" : (actorType == null ? "USER" : actorType);
        repository.lockEntity(entityType, entityId);
        long next = repository.nextVersion(entityType, entityId);
        repository.insertVersion(MarketplaceEntityVersion.builder()
                .id(UUID.randomUUID()).entityType(entityType).entityId(entityId).versionNo(next)
                .action(action).snapshot(writeJson(snapshot))
                .changedFields(writeJson(changedFields == null ? List.of() : changedFields))
                .actorUserId(actorUserId).actorType(effectiveActorType)
                .reason(reason).createdAt(now).build());
        repository.insertAuditEvent(MarketplaceAuditEvent.builder()
                .id(UUID.randomUUID()).organizationId(organizationId).entityType(entityType).entityId(entityId)
                .action(action).actorUserId(actorUserId).actorType(effectiveActorType)
                .reason(reason).metadata("{}").createdAt(now).build());
    }

    @Override
    public List<MarketplaceEntityVersionResponse> list(String entityType, UUID entityId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return repository.findVersions(entityType, entityId, safeSize, Math.max(page, 0) * safeSize)
                .stream().map(this::toResponse).toList();
    }

    private MarketplaceEntityVersionResponse toResponse(MarketplaceEntityVersion value) {
        Map<String, Object> snapshot = JsonUtils.fromJson(value.getSnapshot(), new TypeReference<Map<String, Object>>() {});
        List<String> fields = JsonUtils.fromJson(value.getChangedFields(), new TypeReference<List<String>>() {});
        return MarketplaceEntityVersionResponse.builder().id(value.getId()).entityType(value.getEntityType())
                .entityId(value.getEntityId()).versionNo(value.getVersionNo()).action(value.getAction())
                .snapshot(snapshot == null ? Collections.emptyMap() : snapshot)
                .changedFields(fields == null ? List.of() : fields).actorUserId(value.getActorUserId())
                .actorType(value.getActorType()).reason(value.getReason())
                .restoredFromVersionId(value.getRestoredFromVersionId()).createdAt(value.getCreatedAt()).build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize marketplace history snapshot", ex);
        }
    }
}
