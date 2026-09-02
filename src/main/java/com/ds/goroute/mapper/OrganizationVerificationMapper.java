package com.ds.goroute.mapper;

import com.ds.goroute.entity.OrganizationVerificationDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrganizationVerificationMapper {
    int insertDocument(OrganizationVerificationDocument document);
    List<OrganizationVerificationDocument> findDocumentsByOrganization(@Param("organizationId") UUID organizationId);
    OrganizationVerificationDocument findDocument(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
    int countDocuments(@Param("organizationId") UUID organizationId);
    int updateDocumentReview(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                             @Param("status") String status, @Param("reviewNote") String reviewNote,
                             @Param("reviewedBy") UUID reviewedBy, @Param("reviewedAt") LocalDateTime reviewedAt);
    int deleteDocument(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
