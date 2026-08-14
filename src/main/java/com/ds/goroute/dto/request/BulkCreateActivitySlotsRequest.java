package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkCreateActivitySlotsRequest {
    @NotEmpty
    @Size(max = 120)
    private List<@Valid UpsertActivitySlotRequest> slots;
}
