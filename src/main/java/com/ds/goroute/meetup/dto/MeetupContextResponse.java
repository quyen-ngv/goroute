package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupCannotHostReason;
import com.ds.goroute.type.MeetupCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /v1/api/meetups/context}: whether meetups are open to this user (flag or beta),
 * whether they can host now, and the limits the create flow needs. {@code today} is the
 * Vietnamese date the day chips start from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupContextResponse {

    private boolean enabled;
    private Integer birthYear;
    private boolean canHost;
    private MeetupCannotHostReason cannotHostReason;
    private int maxDaysAhead;
    private int exactMinLeadMinutes;
    private int exactVisibleHours;
    private List<Integer> areaRadiiM;
    private List<MeetupCategory> categories;
    private LocalDate today;
    private String timezone;
}
