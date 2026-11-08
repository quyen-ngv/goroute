package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Stars to exchange into credits. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StarExchangeRequest {
    @Min(1)
    @Max(100_000)
    private int stars;
}
