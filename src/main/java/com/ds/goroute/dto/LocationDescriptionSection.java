package com.ds.goroute.dto;

import com.ds.goroute.enums.LocationDescriptionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDescriptionSection {
    @NotNull(message = "Location description type is required")
    private LocationDescriptionType type;

    @Valid
    @NotNull(message = "Location description content object is required")
    private LocationDescriptionContent content;
}
