package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LinkFoodPlaceRequest {
    @NotNull
    private UUID placeId;
}
