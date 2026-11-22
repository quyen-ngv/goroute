package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.util.List;

/**
 * One weekday of opening hours in the structured form the app and console edit (plan §3.4):
 * {@code {"day": "MONDAY", "closed": false, "allDay": false, "ranges": [{"from": "08:00", "to": "22:00"}]}}.
 * A range whose {@code to} is not after its {@code from} runs past midnight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceHoursDay {
    private DayOfWeek day;
    private boolean closed;
    private boolean allDay;
    private List<PlaceHoursRange> ranges;
}
