package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.OrganizationVerificationDocument;
import com.ds.goroute.mapper.OrganizationVerificationMapper;
import com.ds.goroute.repository.OrganizationVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrganizationVerificationRepositoryImpl implements OrganizationVerificationRepository {
    private final OrganizationVerificationMapper mapper;

    @Override public int insertDocument(OrganizationVerificationDocument document) { return mapper.insertDocument(document); }
    @Override public List<OrganizationVerificationDocument> findDocuments(UUID organizationId) { return mapper.findDocumentsByOrganization(organizationId); }
    @Override public Optional<OrganizationVerificationDocument> findDocument(UUID id, UUID organizationId) {
        return Optional.ofNullable(mapper.findDocument(id, organizationId));
    }
    @Override public int countDocuments(UUID organizationId) { return mapper.countDocuments(organizationId); }
    @Override public int updateDocumentReview(UUID id, UUID organizationId, String status, String reviewNote,
                                              UUID reviewedBy, LocalDateTime reviewedAt) {
        return mapper.updateDocumentReview(id, organizationId, status, reviewNote, reviewedBy, reviewedAt);
    }
    @Override public int deleteDocument(UUID id, UUID organizationId) { return mapper.deleteDocument(id, organizationId); }
}
