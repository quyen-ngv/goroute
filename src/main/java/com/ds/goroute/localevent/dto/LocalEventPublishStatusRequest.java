package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventPublishStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of POST /v1/api/admin/events/{id}/publish-status. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventPublishStatusRequest {

    @NotNull
    private LocalEventPublishStatus publishStatus;

    @NotNull
    private Long expectedVersion;
}
