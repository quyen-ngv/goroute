package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One row of {@code post_places}: a catalogue place a post is about, in the author's order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostPlaceRef {
    private UUID postId;
    private UUID placeId;
    private Integer position;
}
