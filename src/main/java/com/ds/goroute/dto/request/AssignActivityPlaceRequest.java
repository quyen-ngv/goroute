package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignActivityPlaceRequest {

    /** The catalogue place this activity actually is. */
    @NotNull(message = "A place is required")
    private UUID placeId;
}
