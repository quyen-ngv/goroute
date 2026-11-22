package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Pending images per event, batch-loaded for the console list. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImageCount {

    private UUID eventId;
    private Integer imageCount;
}
