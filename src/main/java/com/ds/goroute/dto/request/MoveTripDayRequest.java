package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The position a day's plan moves to; the days between close up and every date stays. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveTripDayRequest {

    @NotNull
    @Min(1) @Max(400)
    private Integer toDay;
}
