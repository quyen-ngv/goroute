package com.ds.goroute.repository;

import com.ds.goroute.entity.OrganizationVerificationDocument;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationVerificationRepository {
    int insertDocument(OrganizationVerificationDocument document);
    List<OrganizationVerificationDocument> findDocuments(UUID organizationId);
    Optional<OrganizationVerificationDocument> findDocument(UUID id, UUID organizationId);
    int countDocuments(UUID organizationId);
    int updateDocumentReview(UUID id, UUID organizationId, String status, String reviewNote,
                             UUID reviewedBy, LocalDateTime reviewedAt);
    int deleteDocument(UUID id, UUID organizationId);
}
