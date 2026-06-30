package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContributionImportResponse {

    private String code;
    private UUID goroutePlaceId;
    private boolean placeAlreadyExists;
    private int reviewsPublished;
    private int contributorsAdded;
}
