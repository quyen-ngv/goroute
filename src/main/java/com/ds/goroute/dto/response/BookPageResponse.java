package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookPageResponse {
    private UUID pageId;
    private String pageType;
    private Integer pageOrder;
    private UUID activityId;
    private String skeletonId;
    private String skeletonKey;
    private Integer skeletonVersion;
    private UUID templateId;
    private String layoutMode;
    private List<BookSlotResponse> slots;
}
