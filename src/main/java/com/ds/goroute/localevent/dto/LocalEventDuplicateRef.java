package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** The event this one probably duplicates (found by the import's fuzzy check). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventDuplicateRef {

    private UUID id;
    private String title;
    private LocalDateTime startAt;
    private String venueName;
}
