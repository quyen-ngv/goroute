package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A re-hosted image of a public event. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImageResponse {

    private String url;
    private String credit;
}
