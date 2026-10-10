package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in viewer's side of one event: whether joining is switched on for them, whether
 * they are going, how many people are, and whether the event still takes new people. The faces
 * of who is going are shown to people going, nobody else.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventAttendanceResponse {

    private boolean enabled;
    private boolean going;
    private long goingCount;
    /** False once the event is cancelled or over; leaving and the chat still work. */
    private boolean joinable;
    private List<Attendee> attendeesPreview;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attendee {
        private UUID id;
        private String name;
        private String avatarUrl;
    }
}
