package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CheckinLikeResponse {
    private UUID checkinId;
    private int likeCount;
    private boolean hasLiked;
}
