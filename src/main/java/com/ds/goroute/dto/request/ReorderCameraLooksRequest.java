package com.ds.goroute.dto.request;

import com.ds.goroute.type.CameraLookKind;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** The looks of one kind in their new display order; each gets its position as sort order. */
@Data
public class ReorderCameraLooksRequest {

    @NotNull
    private CameraLookKind kind;

    @NotEmpty
    @Size(max = 500)
    private List<@NotNull UUID> ids;
}
