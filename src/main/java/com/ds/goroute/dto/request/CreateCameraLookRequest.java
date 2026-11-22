package com.ds.goroute.dto.request;

import com.ds.goroute.type.CameraLookKind;
import com.ds.goroute.type.CameraLookStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A new camera look. The kind and the key are fixed here for good: check-in photos record the
 * key they were taken with. The shape of {@code definition} is checked by the service against
 * the kind (CameraLookDefinitionValidator).
 */
@Data
public class CreateCameraLookRequest {

    @NotNull
    private CameraLookKind kind;

    /** Stable code inside the kind, lowercase snake_case. Equal to a bundled look's id, it replaces that look in the app. */
    @NotBlank
    @Size(max = CameraLookKeys.MAX_LENGTH)
    @Pattern(regexp = CameraLookKeys.PATTERN, message = "key must be lowercase snake_case, e.g. old_town")
    private String key;

    /** Language tag to name; {@code en} is required. */
    @NotEmpty
    private Map<String, String> names;

    @NotNull
    private JsonNode definition;

    @Size(max = 2000)
    private String thumbnailUrl;

    @Min(0)
    @Max(100000)
    private Integer sortOrder;

    /** Absent means DRAFT. */
    private CameraLookStatus status;

    /** UTC. When set, the look is served only from this moment. */
    private LocalDateTime startsAt;

    /** UTC. When set, the look is served only before this moment. */
    private LocalDateTime endsAt;

    @Size(max = 2000)
    private String source;

    @Size(max = 2000)
    private String licenseNote;
}
