package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Empty days to insert into a trip, before day {@code beforeDay} (the day after the last appends them). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsertTripDaysRequest {

    @NotNull
    @Min(1) @Max(401)
    private Integer beforeDay;

    @Min(1) @Max(30)
    private Integer count = 1;
}
