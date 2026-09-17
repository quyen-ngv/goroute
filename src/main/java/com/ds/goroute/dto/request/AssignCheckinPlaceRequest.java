package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignCheckinPlaceRequest {

    /** The catalogue place this check-in actually happened at. */
    @NotNull(message = "A place is required")
    private UUID placeId;

    /** Why the operator moved it. Kept with the snapshot of the original location. */
    @Size(max = 500)
    private String reason;
}
