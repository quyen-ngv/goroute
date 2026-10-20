package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The posts the reader has now seen; their follower notifications are marked read. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkFollowingMomentsReadRequest {

    @NotEmpty
    @Size(max = 50)
    private List<@Valid FollowingMomentRefRequest> posts;
}
