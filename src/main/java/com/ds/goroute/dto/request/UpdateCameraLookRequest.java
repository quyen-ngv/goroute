package com.ds.goroute.dto.request;

import com.ds.goroute.type.CameraLookStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A partial update: a null field keeps the stored value, a blank string clears an optional text.
 * The kind and the key cannot be changed. A date has no blank form, so each end of the window
 * has its own clear flag.
 */
@Data
public class UpdateCameraLookRequest {

    /** Replaces the whole map when present; {@code en} is required. */
    private Map<String, String> names;

    /** Replaces the whole definition when present. */
    private JsonNode definition;

    @Size(max = 2000)
    private String thumbnailUrl;

    @Min(0)
    @Max(100000)
    private Integer sortOrder;

    private CameraLookStatus status;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    /** True removes the start of the window; wins over {@code startsAt}. */
    private Boolean clearStartsAt;

    /** True removes the end of the window; wins over {@code endsAt}. */
    private Boolean clearEndsAt;

    @Size(max = 2000)
    private String source;

    @Size(max = 2000)
    private String licenseNote;
}
