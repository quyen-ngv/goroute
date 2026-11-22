package com.ds.goroute.type;

/**
 * What a row of {@code camera_looks} is. A key is unique inside its kind and the kind never
 * changes after creation: check-in photos record the filter and the frame in separate columns.
 */
public enum CameraLookKind {
    /** A colour look: adjust values or a colour matrix, optionally a LUT image. */
    FILTER,
    /** A transparent PNG laid over the photo and/or text slots; shown in the app as a camera. */
    FRAME,
    /** A shelf the filters are sorted onto (V294): a key, names and an order, no definition. */
    GROUP
}
