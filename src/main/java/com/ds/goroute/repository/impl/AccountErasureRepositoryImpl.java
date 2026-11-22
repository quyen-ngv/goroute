package com.ds.goroute.repository.impl;

import com.ds.goroute.config.cache.AuthSubjectCache;
import com.ds.goroute.mapper.AccountErasureMapper;
import com.ds.goroute.repository.AccountErasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountErasureRepositoryImpl implements AccountErasureRepository {

    private final AccountErasureMapper mapper;
    private final AuthSubjectCache authSubjectCache;

    @Override
    public List<UUID> findLiveCheckinIds(UUID userId) {
        return mapper.findLiveCheckinIds(userId);
    }

    @Override
    public List<UUID> findReviewIds(UUID userId) {
        return mapper.findReviewIds(userId);
    }

    @Override
    public List<UUID> findSoloOwnedTripIds(UUID userId) {
        return mapper.findSoloOwnedTripIds(userId);
    }

    @Override
    public List<UUID> findUnerasedDeletedUserIds(String anonymousEmailSuffix, int limit) {
        return mapper.findUnerasedDeletedUserIds(anonymousEmailSuffix, limit);
    }

    @Override
    public void deletePrivateRecords(UUID userId) {
        mapper.deleteFollows(userId);
        mapper.deleteBlocks(userId);
        mapper.deleteSavedPlaceMarks(userId);
        mapper.deleteSavedPosts(userId);
        mapper.deleteFeedImpressions(userId);
        mapper.deleteFeedFeedback(userId);
        mapper.deleteAuthorAffinity(userId);
        mapper.deleteMapSearchUsage(userId);
        mapper.deleteNotifications(userId);
        mapper.deleteScheduledDeliveries(userId);
    }

    @Override
    public void deleteAssistantData(UUID userId) {
        mapper.deleteAssistantProfile(userId);
        // Keyed by thread, so they go before the threads they point at.
        mapper.deleteAssistantMemories(userId);
        mapper.deleteAssistantProposals(userId);
        mapper.deleteAssistantTurns(userId);
        mapper.deleteAssistantThreads(userId);
    }

    @Override
    public void clearAiCallPayloads(UUID userId) {
        mapper.clearAiCallPayloads(userId);
    }

    @Override
    public void removeComments(UUID userId) {
        mapper.softDeleteContentComments(userId);
        mapper.softDeleteActivityComments(userId);
    }

    @Override
    public void withdrawFromMeetups(UUID userId) {
        mapper.cancelHostedMeetups(userId);
        mapper.leaveMeetups(userId);
    }

    @Override
    public boolean anonymiseUser(UUID userId, String email, String fullName) {
        boolean updated = mapper.anonymiseUser(userId, email, fullName) == 1;
        authSubjectCache.evict(userId);
        return updated;
    }
}
