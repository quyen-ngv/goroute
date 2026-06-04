package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchLinkFoodPlacesRequest {
    @NotEmpty
    private List<UUID> placeIds;
}
