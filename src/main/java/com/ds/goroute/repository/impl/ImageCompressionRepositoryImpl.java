package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ImageCompressionGroupTotal;
import com.ds.goroute.entity.ImageCompressionRecord;
import com.ds.goroute.mapper.ImageCompressionMapper;
import com.ds.goroute.repository.ImageCompressionRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class ImageCompressionRepositoryImpl implements ImageCompressionRepository {

    /** Keeps each IN list well under the 1000-item rule. */
    private static final int KEY_BATCH = 500;

    private final ImageCompressionMapper mapper;

    public ImageCompressionRepositoryImpl(ImageCompressionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ImageCompressionRecord record) {
        mapper.upsert(record);
    }

    @Override
    public Set<String> findRecordedKeys(Collection<String> keys) {
        Set<String> recorded = new HashSet<>();
        if (keys == null || keys.isEmpty()) {
            return recorded;
        }
        List<String> all = new ArrayList<>(new HashSet<>(keys));
        for (int start = 0; start < all.size(); start += KEY_BATCH) {
            recorded.addAll(mapper.findRecordedKeys(all.subList(start, Math.min(all.size(), start + KEY_BATCH))));
        }
        return recorded;
    }

    @Override
    public List<ImageCompressionGroupTotal> totalsByGroup() {
        return mapper.totalsByGroup();
    }
}
