package com.ds.goroute.dto.response;

import com.ds.goroute.entity.AiApiCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One page of AI call rows (no payloads) plus the filter choices the page offers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiApiCallPageResponse {
    private List<AiApiCall> items;
    private long total;
    private int page;
    private int size;
    private List<String> features;
    private List<String> models;
}
