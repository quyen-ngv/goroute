package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * New dates for a trip and what happens to its plan.
 *
 * <p>{@code keepDates}: a day whose date is still in the new range keeps its date and plan;
 * otherwise the plan keeps its order from the new start. Days left out are removed with their
 * activities, unless {@code mergeInto} maps the old day number to the new day (1-based) their
 * activities join.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleTripRequest {

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private boolean keepDates = true;

    private Map<Integer, Integer> mergeInto = new LinkedHashMap<>();
}
