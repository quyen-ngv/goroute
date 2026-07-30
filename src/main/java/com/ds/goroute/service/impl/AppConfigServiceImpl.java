package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpsertAppConfigRequest;
import com.ds.goroute.dto.response.AppConfigResponse;
import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AppConfigServiceImpl implements AppConfigService {
    private static final String PUBLIC_LABEL = "CONFIG";
    private static final String PUBLIC_KEY = "PUBLIC_CONFIG";
    private static final Pattern PUBLIC_SELECTOR = Pattern.compile(
            "\\{\\s*label\\s*=\\s*'([^']+)'\\s*,\\s*key\\s*=\\s*'([^']+)'\\s*}", Pattern.CASE_INSENSITIVE);

    private final AppConfigRepository repository;

    @Override
    public List<AppConfigResponse> listPublic() {
        AppConfig publicConfig = repository.findActiveByLabelAndKey(PUBLIC_LABEL, PUBLIC_KEY).orElse(null);
        if (publicConfig == null) return List.of();

        Matcher matcher = PUBLIC_SELECTOR.matcher(publicConfig.getValue());
        Map<UUID, AppConfigResponse> result = new LinkedHashMap<>();
        while (matcher.find()) {
            String label = normalize(matcher.group(1));
            String key = normalize(matcher.group(2));
            repository.findActiveByLabelAndKey(label, key)
                    .map(this::response)
                    .ifPresent(config -> result.put(config.getId(), config));
        }
        return new ArrayList<>(result.values());
    }

    @Override
    public List<AppConfigResponse> adminList(String query, String label, Boolean active, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        return repository.findAdmin(clean(query), label == null ? null : normalize(label), active,
                safeSize, safePage * safeSize).stream().map(this::response).toList();
    }

    @Override public AppConfigResponse adminGet(UUID id) { return response(required(id)); }

    @Override
    @Transactional
    public AppConfigResponse adminCreate(UpsertAppConfigRequest request) {
        LocalDateTime now = LocalDateTime.now();
        AppConfig config = AppConfig.builder()
                .id(UUID.randomUUID())
                .label(normalize(request.getLabel()))
                .key(normalize(request.getKey()))
                .value(request.getValue().trim())
                .description(clean(request.getDescription()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .dataVersion(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            repository.insert(config);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Config label/key already exists");
        }
        return response(config);
    }

    @Override
    @Transactional
    public AppConfigResponse adminUpdate(UUID id, UpsertAppConfigRequest request) {
        AppConfig config = required(id);
        long expectedVersion = request.getExpectedVersion() == null
                ? config.getDataVersion() : request.getExpectedVersion();
        config.setLabel(normalize(request.getLabel()));
        config.setKey(normalize(request.getKey()));
        config.setValue(request.getValue().trim());
        config.setDescription(clean(request.getDescription()));
        config.setIsActive(request.getIsActive() == null || request.getIsActive());
        config.setDataVersion(expectedVersion);
        config.setUpdatedAt(LocalDateTime.now());
        try {
            if (repository.update(config) != 1) {
                throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                        "Config was changed by another administrator");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Config label/key already exists");
        }
        config.setDataVersion(expectedVersion + 1);
        return response(config);
    }

    @Override
    @Transactional
    public void adminDelete(UUID id) {
        required(id);
        repository.delete(id);
    }

    private AppConfig required(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Config not found"));
    }

    private AppConfigResponse response(AppConfig config) {
        return AppConfigResponse.builder().id(config.getId()).label(config.getLabel()).key(config.getKey())
                .value(config.getValue()).description(config.getDescription()).isActive(config.getIsActive())
                .dataVersion(config.getDataVersion()).createdAt(config.getCreatedAt()).updatedAt(config.getUpdatedAt())
                .build();
    }

    private String normalize(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
