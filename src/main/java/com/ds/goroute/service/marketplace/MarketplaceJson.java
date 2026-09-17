package com.ds.goroute.service.marketplace;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * JSONB column codec for marketplace rows. Reads are lenient — a malformed or empty column
 * yields the empty value rather than failing a public page — writes are strict.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceJson {
    private final ObjectMapper objectMapper;

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Cannot serialize marketplace data");
        }
    }

    public <T> List<T> readList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<Map<String, Object>> readMaps(String json) {
        return read(json, new TypeReference<>() {}, List.of());
    }

    public Map<String, Object> readMap(String json) {
        return read(json, new TypeReference<>() {}, Map.of());
    }

    public <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
