package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributedPlaceResponse {
    private UUID placeId;
    private String placeGoogleId;
    private String title;
    private String address;
    private String thumbnail;
    private UUID contributionId;
    private UUID contributionGroupId;
}
