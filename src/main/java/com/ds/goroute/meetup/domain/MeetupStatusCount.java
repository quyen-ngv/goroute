package com.ds.goroute.meetup.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How many of a host's meetups are in one stored status; the console's host history card. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupStatusCount {

    private String status;
    private long total;
}
