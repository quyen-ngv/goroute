package com.ds.goroute.entity;

import lombok.Data;

import java.util.UUID;

/** Projection used only to enrich a page of check-ins without one count query per row. */
@Data
public class CheckinLikeCount {
    private UUID checkinId;
    private Integer likeCount;
}
