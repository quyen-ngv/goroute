package com.ds.goroute.dto.request;

import com.ds.goroute.type.FollowingPostType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One post of someone the reader follows. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowingMomentRefRequest {

    @NotNull
    private FollowingPostType postType;

    @NotNull
    private UUID postId;
}
