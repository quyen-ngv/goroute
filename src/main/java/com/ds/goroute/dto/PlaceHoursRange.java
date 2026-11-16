package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One opening window of a {@link PlaceHoursDay}, 24-hour {@code HH:mm} clocks ("24:00" is midnight). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceHoursRange {
    private String from;
    private String to;
}
