package com.ds.goroute.entity;

import lombok.Data;

import java.util.UUID;

/** Projection used only to put a comment count on a page of posts without one query per row. */
@Data
public class ContentCommentCount {
    private UUID contentId;
    private Integer commentCount;
}
