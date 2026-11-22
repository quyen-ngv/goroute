package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * One visible step of Pengoo's work ("thinking"). The app translates {@code key} with
 * {@code params}; a {@code thought} step carries the model's own sentence in {@code text}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantStepResponse {
    private String key;
    private Map<String, Object> params;
    private String text;
    private String at;
}
