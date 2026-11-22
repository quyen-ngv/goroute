package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventImageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** An image row as the console sees it, with its re-hosting state. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLocalEventImageResponse {

    private UUID id;
    private Integer position;
    /** Our re-hosted URL; null until the job has stored it. */
    private String url;
    private String sourceUrl;
    private String credit;
    private LocalEventImageStatus status;
    private Integer attempts;
    private String failureReason;
}
