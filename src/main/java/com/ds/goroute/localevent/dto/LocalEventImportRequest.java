package com.ds.goroute.localevent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of POST /v1/api/admin/events/import. Items stay raw JSON until each is read on its own,
 * so one malformed item fails alone (FAILED) instead of rejecting the whole batch. Unknown
 * top-level keys are ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalEventImportRequest {

    private Boolean dryRun;
    private List<JsonNode> events;
}
