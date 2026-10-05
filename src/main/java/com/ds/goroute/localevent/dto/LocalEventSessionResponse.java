package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One session of an event; times UTC without offset. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventSessionResponse {

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String label;
    private LocalEventStatus status;
}
