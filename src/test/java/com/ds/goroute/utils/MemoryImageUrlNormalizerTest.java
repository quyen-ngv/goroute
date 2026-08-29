package com.ds.goroute.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryImageUrlNormalizerTest {

    @Test
    void extractsUrlFromLegacyUploadOutcomeStoredAsMemoryUrl() {
        String storedValue = "{originalFilename: image.jpg, url: https://cdn.example/memory.webp, "
                + "rejectedCategory: null, accepted: true}";

        assertThat(MemoryImageUrlNormalizer.normalize(storedValue))
                .contains("https://cdn.example/memory.webp");
    }

    @Test
    void acceptsNormalHttpUrlAndRejectsNonUrls() {
        assertThat(MemoryImageUrlNormalizer.normalize(" https://cdn.example/photo.webp "))
                .contains("https://cdn.example/photo.webp");
        assertThat(MemoryImageUrlNormalizer.normalize("{originalFilename: image.jpg}"))
                .isEmpty();
    }
}
