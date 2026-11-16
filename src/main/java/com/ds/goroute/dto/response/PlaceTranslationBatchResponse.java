package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTranslationBatchResponse {
    private int inserted;
    private int updated;
    /** Rows not written because a higher-ranked source (an admin's MANUAL name) holds them. */
    private int skippedManual;
    private int skippedInvalid;
    /** Google place ids that match no place, once each, in request order. */
    private List<String> notFound;
}
