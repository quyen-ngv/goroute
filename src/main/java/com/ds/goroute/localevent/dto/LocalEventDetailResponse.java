package com.ds.goroute.localevent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/** GET /v1/api/public/events/{id}. Images are the DONE (re-hosted) ones, in position order. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LocalEventDetailResponse extends LocalEventDetailFields {

    private List<LocalEventImageResponse> images;
}
