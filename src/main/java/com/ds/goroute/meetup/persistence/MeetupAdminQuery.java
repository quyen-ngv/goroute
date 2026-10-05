package com.ds.goroute.meetup.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Filters of the console meetup list. {@code sort} is the name of a
 * {@link com.ds.goroute.type.MeetupAdminSort} constant, mapped to a fixed ORDER BY in the XML.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupAdminQuery {

    private List<String> statuses;
    private List<String> categories;
    private String search;
    private String provinceCode;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    /** When true, meetups whose window has passed are left out. */
    private boolean hidePast;
    private LocalDateTime now;
    private String sort;
    private int limit;
    private int offset;
}
