package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceAttributeGroup;
import com.ds.goroute.type.PlaceAttributeType;

import java.util.List;
import java.util.Map;

public record PlaceAttributeDefinition(
        String key,
        String label,
        PlaceAttributeGroup group,
        String groupLabel,
        PlaceAttributeType type,
        List<String> allowedValues,
        Map<String, Integer> rankByValue,
        boolean multiple) {
}
