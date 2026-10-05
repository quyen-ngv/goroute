package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** An existing event that looks like the one being imported, with its title similarity (0..1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventDuplicateCandidate {

    private UUID id;
    private String title;
    private LocalDateTime startAt;
    private String venueName;
    private Double score;
}
