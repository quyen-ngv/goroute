package com.ds.goroute.entity;

import lombok.Data;

import java.util.UUID;

/** The image columns of one place and its compression record, read for place image compression. */
@Data
public class PlaceImageState {
    private UUID id;
    /** Google place id; also the storage folder of the place's images when present. */
    private String placeId;
    private String thumbnail;
    /** JSON array of {@code {image, title}}. */
    private String images;
    /** {@code places.image_compression} as JSON text; null when the place was never compressed. */
    private String imageCompression;
}
