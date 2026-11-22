package com.ds.goroute.dto.request;

import com.ds.goroute.type.TrackDeletionScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Removes part of the caller's own route (TR-13, TR-14). Points can only be removed, never
 * moved. {@code date} is required for RANGES and DAY; ranges are inclusive epoch millis.
 */
@Data
public class TrackDeletionRequest {

    public static final int MAX_RANGES = 20;

    @NotNull
    private TrackDeletionScope scope;

    private LocalDate date;

    @Valid
    @Size(max = MAX_RANGES)
    private List<Range> ranges;

    @Data
    public static class Range {
        @NotNull
        private Long fromMs;

        @NotNull
        private Long toMs;
    }
}
