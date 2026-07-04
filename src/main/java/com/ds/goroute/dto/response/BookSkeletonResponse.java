package com.ds.goroute.dto.response;

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
public class BookSkeletonResponse {
    private UUID id;
    private String skeletonKey;
    private Integer version;
    private String name;
    private String pageType;
    private Integer photoCount;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private Boolean isActive;
    private List<BookSlotDefResponse> slotDefs;
}
