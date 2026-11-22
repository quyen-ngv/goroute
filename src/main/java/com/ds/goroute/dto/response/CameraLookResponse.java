package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** A camera look as the console sees it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CameraLookResponse {
    private UUID id;
    /** FILTER or FRAME. */
    private String kind;
    private String key;
    /** Language tag to name. */
    private Map<String, String> names;
    private JsonNode definition;
    private String thumbnailUrl;
    private Integer sortOrder;
    private String status;
    /** UTC. */
    private LocalDateTime startsAt;
    /** UTC. */
    private LocalDateTime endsAt;
    private String source;
    private String licenseNote;
    private Integer version;
    /** Live check-in photos taken with this look. */
    private Long usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
