package com.ds.goroute.repository;

import com.ds.goroute.entity.ImageCompressionGroupTotal;
import com.ds.goroute.entity.ImageCompressionRecord;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** {@code image_compressions} (V280). */
public interface ImageCompressionRepository {

    void save(ImageCompressionRecord record);

    /** The keys among {@code keys} that are already recorded; batched, any size. */
    Set<String> findRecordedKeys(Collection<String> keys);

    List<ImageCompressionGroupTotal> totalsByGroup();
}
