package com.ds.goroute.repository.impl;

import com.ds.goroute.mapper.SavedPostMapper;
import com.ds.goroute.repository.SavedPostRepository;
import com.ds.goroute.type.SavedPostType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SavedPostRepositoryImpl implements SavedPostRepository {

    private final SavedPostMapper mapper;

    @Override
    public Set<UUID> findSavedIds(UUID userId, SavedPostType postType, Collection<UUID> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(mapper.findSavedIds(userId, postType, List.copyOf(postIds)));
    }
}
