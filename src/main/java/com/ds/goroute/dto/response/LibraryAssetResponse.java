package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A library asset as the console sees it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LibraryAssetResponse {
    private UUID id;
    private String collection;
    private String key;
    /** Name in the request language, falling back to English. */
    private String name;
    /** Every name that is set, keyed by language code (vi, en, ja, ko). */
    private Map<String, String> names;
    private String description;
    private List<String> tags;
    private String imageUrl;
    private String source;
    private String sourceUrl;
    private String licenseNote;
    private String status;
    private Integer version;
    /** How many records use the asset; counted for the ingredient collection. */
    private Integer usageCount;
    /** The records that use it; only on the detail endpoint. */
    private List<LibraryAssetUsageResponse> usedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
