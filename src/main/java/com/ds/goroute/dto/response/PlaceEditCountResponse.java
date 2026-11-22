package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET /v1/api/admin/place-edit-suggestions/count. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditCountResponse {
    private long count;
}
