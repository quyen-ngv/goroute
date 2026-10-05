package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An image of the event. A URL already on our storage is used as is; any other HTTPS URL is
 * queued and re-hosted by the image job. The first image becomes the cover.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImageRequest {

    private String url;
    private String credit;
}
