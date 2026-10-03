package com.ds.goroute.dto.request;

import com.ds.goroute.dto.TrackPointsPayload;
import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackSource;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * One batch of recorded points. {@link #clientChunkId} is the idempotency key: the phone
 * resends the same chunk until it gets an answer, and a resend never stores twice.
 */
@Data
public class UploadTrackChunkRequest {

    @NotNull
    private UUID clientChunkId;

    @NotNull
    private UUID clientSessionId;

    @NotNull
    private TrackAccuracyMode accuracyMode;

    @NotNull
    private TrackSource source;

    /** Structure (lengths, increasing time, size cap) is checked by {@code TrackPointsCodec}. */
    @NotNull
    private TrackPointsPayload points;
}
