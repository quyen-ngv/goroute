package com.ds.goroute.service;

import com.ds.goroute.dto.request.DecideOrganizationVerificationRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationVerificationDocumentResponse;
import com.ds.goroute.dto.response.OrganizationVerificationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.VerificationQueueItemResponse;
import com.ds.goroute.type.OrganizationVerificationDocumentKind;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Organization verification: the partner uploads supporting documents and submits, an admin
 * decides. Status lives on {@code host_organizations.verification_status}; documents in
 * {@code organization_verification_documents}.
 */
public interface OrganizationVerificationService {
    OrganizationVerificationResponse get(UUID actorUserId, UUID organizationId);
    OrganizationVerificationDocumentResponse uploadDocument(UUID actorUserId, UUID organizationId,
                                                            OrganizationVerificationDocumentKind kind,
                                                            String note, MultipartFile file);
    void deleteDocument(UUID actorUserId, UUID organizationId, UUID documentId);
    OrganizationVerificationResponse submit(UUID actorUserId, UUID organizationId);

    PageResponse<VerificationQueueItemResponse> adminQueue(int page, int size);
    OrganizationVerificationResponse adminGet(UUID organizationId);
    HostOrganizationResponse adminDecide(UUID actorUserId, UUID organizationId, DecideOrganizationVerificationRequest request);
}
