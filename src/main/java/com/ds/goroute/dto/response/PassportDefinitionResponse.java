package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PassportDefinitionResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    /** Raw English copy for the operator catalogue; null on traveller-facing reads, which are already localized. */
    private String nameEn;
    private String descriptionEn;
    private String coverImageUrl;
    @JsonProperty("isActive")
    private boolean isActive;
    private int displayOrder;
    private List<UUID> locationImageIds;
    /** Legacy province scope, present only for older rows/clients. */
    private List<String> provinceCodes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
