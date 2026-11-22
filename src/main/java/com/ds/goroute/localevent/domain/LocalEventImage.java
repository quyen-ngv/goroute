package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One {@code local_event_images} row; {@code url} is null until the image is re-hosted. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImage {

    private UUID id;
    private UUID eventId;
    private Integer position;
    private String sourceUrl;
    private String url;
    private String credit;
    private String status;
    private Integer attempts;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
