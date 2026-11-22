package com.ds.goroute.meetup.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A grouped count keyed by an id (JOINED seats per meetup, reports per meetup). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupCount {

    private UUID id;
    private long total;
}
