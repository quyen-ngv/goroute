package com.ds.goroute.type;

/** What compressed a stored image (column {@code image_compressions.source}). */
public enum ImageCompressionSource {
    /** Right after upload, when {@code IMAGE_COMPRESSION.AUTO_ENABLED} is on. */
    AUTO,
    /** The console's bulk run over storage groups. */
    BULK,
    /** The console's per-place compression. */
    PLACE
}
