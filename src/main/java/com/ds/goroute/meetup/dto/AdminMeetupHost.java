package com.ds.goroute.meetup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The host card of the console review panel: who they are and how their meetups went so far.
 * The birth year is shown to reviewers only, never to other users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMeetupHost {

    private UUID id;
    private String name;
    private String username;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private Integer birthYear;
    private long hostedTotal;
    private long approvedTotal;
    private long rejectedTotal;
    private long removedTotal;
    private long cancelledTotal;
    /** The host's five most recent meetups, newest first (this one included). */
    private List<Recent> recent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recent {
        private UUID id;
        private String title;
        private String status;
        private LocalDate meetupDate;
    }
}
