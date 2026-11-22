package com.ds.goroute.meetup.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One user report against a meetup, as the console's review panel lists the latest ones. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupReportReason {

    private String reason;
    private String note;
    private LocalDateTime createdAt;
}
