package com.ds.goroute.dto.response;

import com.ds.goroute.type.PlaceImportSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlaceImportRunResponse {
    private PlaceImportSourceType sourceType;
    private Integer targetedUserCount;
    private boolean queued;
}
