package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * The looks the app offers, in the bundled manifest's own shape
 * ({@code assets/camera_looks/looks.json}): each entry is {@code id}, {@code name} and the
 * fields of its definition laid flat beside them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraLookManifestResponse {
    /** Changes whenever the served set changes; the app refetches assets only then. */
    private String version;
    private List<Map<String, Object>> filters;
    private List<Map<String, Object>> frames;
    /** The film shelves in order, each {@code id} and {@code name}; a filter names one in {@code group}. */
    private List<Map<String, Object>> groups;
}
