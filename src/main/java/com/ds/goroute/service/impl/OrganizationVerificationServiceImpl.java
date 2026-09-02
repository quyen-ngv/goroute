package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.DecideOrganizationVerificationRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationVerificationDocumentResponse;
import com.ds.goroute.dto.response.OrganizationVerificationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.VerificationQueueItemResponse;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationVerificationDocument;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.VerificationQueueEntry;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.OrganizationVerificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.OrganizationVerificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.OrganizationVerificationDocumentKind;
import com.ds.goroute.type.OrganizationVerificationDocumentStatus;
import com.ds.goroute.type.OrganizationVerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationVerificationServiceImpl implements OrganizationVerificationService {
    /**
     * The upload door only accepts images (JPEG/PNG/WEBP) and checks their content; PDFs are refused
     * there. The hint is surfaced in the response so the console can say so before the user tries.
     */
    static final String UPLOAD_HINT = "Documents must be JPEG, PNG or WEBP images (photo or scan); PDF is not accepted.";
    private static final int MAX_DOCUMENTS = 20;
    private static final int MAX_QUEUE_PAGE = 100;

    private final HostOrganizationRepository organizations;
    private final OrganizationVerificationRepository documents;
    private final UserRepository users;
    private final PartnerAuthorizationService authorization;
    private final FileUploadService fileUploadService;
    private final MarketplaceHistoryService historyService;
    private final NotificationService notificationService;
    private final NotificationTemplateRenderer templateRenderer;

    @Override
    @Transactional(readOnly = true)
    public OrganizationVerificationResponse get(UUID actorUserId, UUID organizationId) {
        HostOrganization organization = authorization.requirePermission(organizationId, actorUserId, "ORGANIZATION_READ");
        return toResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationVerificationDocumentResponse uploadDocument(UUID actorUserId, UUID organizationId,
            OrganizationVerificationDocumentKind kind, String note, MultipartFile file) {
        HostOrganization organization = authorization.requirePermission(organizationId, actorUserId, "ORGANIZATION_WRITE");
        requireEditable(organization);
        if (kind == null) throw new BusinessException(ErrorConstant.BAD_REQUEST, "Document kind is required");
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorConstant.BAD_REQUEST, "A document file is required");
        if (documents.countDocuments(organizationId) >= MAX_DOCUMENTS) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "At most " + MAX_DOCUMENTS + " documents can be attached");
        }
        ImageUploadRequest upload = ImageUploadRequest.of(actorUserId,
                ImageUploadRequest.ImageEntryPoint.PARTNER_VERIFICATION, "partners/verification/" + organizationId);
        ImageUploadOutcome outcome;
        try {
            outcome = fileUploadService.uploadImage(upload, file);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, ex.getMessage() + ". " + UPLOAD_HINT);
        }
        if (!outcome.isAccepted()) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    (outcome.failureMessage() == null ? "Document was not accepted" : outcome.failureMessage()) + ". " + UPLOAD_HINT);
        }
        OrganizationVerificationDocument document = OrganizationVerificationDocument.builder()
                .id(UUID.randomUUID()).organizationId(organizationId).kind(kind.name())
                .fileUrl(outcome.url()).fileName(safeFileName(file.getOriginalFilename())).note(blankToNull(note))
                .status(OrganizationVerificationDocumentStatus.SUBMITTED.name()).uploadedBy(actorUserId)
                .createdAt(LocalDateTime.now()).build();
        documents.insertDocument(document);
        historyService.audit(organizationId, "ORGANIZATION_VERIFICATION_DOCUMENT", document.getId(), "UPLOADED",
                actorUserId, "USER", null, Map.of("kind", kind.name()));
        return toDocumentResponse(document);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID actorUserId, UUID organizationId, UUID documentId) {
        HostOrganization organization = authorization.requirePermission(organizationId, actorUserId, "ORGANIZATION_WRITE");
        if (OrganizationVerificationStatus.VERIFIED.name().equals(organization.getVerificationStatus())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Documents of a verified organization cannot be removed");
        }
        documents.findDocument(documentId, organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Verification document not found"));
        documents.deleteDocument(documentId, organizationId);
        historyService.audit(organizationId, "ORGANIZATION_VERIFICATION_DOCUMENT", documentId, "DELETED",
                actorUserId, "USER", null, null);
    }

    @Override
    @Transactional
    public OrganizationVerificationResponse submit(UUID actorUserId, UUID organizationId) {
        HostOrganization organization = authorization.requirePermission(organizationId, actorUserId, "ORGANIZATION_WRITE");
        String status = organization.getVerificationStatus();
        if (!OrganizationVerificationStatus.UNVERIFIED.name().equals(status)
                && !OrganizationVerificationStatus.REJECTED.name().equals(status)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "Verification can only be submitted from UNVERIFIED or REJECTED (current: " + status + ")");
        }
        if (documents.countDocuments(organizationId) == 0) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Attach at least one document before submitting");
        }
        LocalDateTime now = LocalDateTime.now();
        organization.setVerificationStatus(OrganizationVerificationStatus.PENDING.name());
        organization.setVerificationSubmittedAt(now);
        organization.setVerificationReason(null);
        organization.setVerificationDecidedAt(null);
        organization.setVerificationDecidedBy(null);
        organization.setUpdatedAt(now);
        persistVerificationState(organization);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "VERIFICATION_SUBMITTED",
                organization, List.of("verificationStatus"), actorUserId, "USER", null);
        return toResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VerificationQueueItemResponse> adminQueue(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_QUEUE_PAGE);
        int safePage = Math.max(page, 0);
        List<VerificationQueueItemResponse> items = organizations.findVerificationQueue(safeSize, safePage * safeSize)
                .stream().map(this::toQueueItem).toList();
        return PageResponse.of(items, organizations.countVerificationQueue(), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationVerificationResponse adminGet(UUID organizationId) {
        return toResponse(required(organizationId));
    }

    @Override
    @Transactional
    public HostOrganizationResponse adminDecide(UUID actorUserId, UUID organizationId,
                                                DecideOrganizationVerificationRequest request) {
        HostOrganization organization = required(organizationId);
        if (!OrganizationVerificationStatus.PENDING.name().equals(organization.getVerificationStatus())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization has no pending verification to decide");
        }
        if (request.getExpectedVersion() == null || !request.getExpectedVersion().equals(organization.getDataVersion())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed; reload and retry");
        }
        boolean approve = Boolean.TRUE.equals(request.getApprove());
        String reason = blankToNull(request.getReason());
        if (!approve && reason == null) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "A reason is required when rejecting verification");
        }
        LocalDateTime now = LocalDateTime.now();
        applyDocumentDecisions(organizationId, actorUserId, request.getDocumentDecisions(), now);

        organization.setVerificationStatus((approve ? OrganizationVerificationStatus.VERIFIED
                : OrganizationVerificationStatus.REJECTED).name());
        organization.setVerificationReason(approve ? null : reason);
        organization.setVerificationDecidedAt(now);
        organization.setVerificationDecidedBy(actorUserId);
        organization.setUpdatedAt(now);
        persistVerificationState(organization);
        historyService.record(organizationId, "HOST_ORGANIZATION", organizationId, "VERIFICATION_DECIDED",
                organization, List.of("verificationStatus"), actorUserId, "ADMIN", reason);
        notifyOwner(organization, approve, reason, actorUserId);
        return HostOrganizationServiceImpl.toResponse(organization);
    }

    private void applyDocumentDecisions(UUID organizationId, UUID actorUserId,
                                        List<DecideOrganizationVerificationRequest.DocumentDecision> decisions,
                                        LocalDateTime now) {
        if (decisions == null) return;
        for (DecideOrganizationVerificationRequest.DocumentDecision decision : decisions) {
            if (decision.getStatus() == OrganizationVerificationDocumentStatus.SUBMITTED) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST, "Document decision must be ACCEPTED or REJECTED");
            }
            documents.findDocument(decision.getDocumentId(), organizationId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Verification document not found"));
            documents.updateDocumentReview(decision.getDocumentId(), organizationId, decision.getStatus().name(),
                    blankToNull(decision.getReviewNote()), actorUserId, now);
        }
    }

    private void persistVerificationState(HostOrganization organization) {
        long expected = organization.getDataVersion();
        if (organizations.updateVerificationState(organization) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Organization was changed; reload and retry");
        }
        organization.setDataVersion(expected + 1);
    }

    private void notifyOwner(HostOrganization organization, boolean approve, String reason, UUID actorUserId) {
        UUID ownerId = organization.getOwnerUserId();
        if (ownerId == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("organizationId", organization.getId().toString());
        data.put("organizationName", organization.getDisplayName());
        data.put("verificationStatus", organization.getVerificationStatus());
        data.put("decisionLabel", approve ? "approved" : "rejected");
        data.put("reason", reason == null ? "" : reason);
        try {
            String language = users.findById(ownerId).map(User::getLanguage).orElse(null);
            NotificationMessage message = templateRenderer.render(NotificationType.PARTNER_VERIFICATION_DECIDED, data, language);
            notificationService.createNotification(ownerId, null, NotificationType.PARTNER_VERIFICATION_DECIDED,
                    message.title(), message.body(), data, actorUserId);
        } catch (RuntimeException ex) {
            // The decision is already persisted; a failed notification must not undo it.
            log.warn("Could not notify owner {} about verification decision for organization {}: {}",
                    ownerId, organization.getId(), ex.getMessage());
        }
    }

    private void requireEditable(HostOrganization organization) {
        if (OrganizationVerificationStatus.VERIFIED.name().equals(organization.getVerificationStatus())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Organization is already verified");
        }
    }

    private HostOrganization required(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Partner organization not found"));
    }

    private OrganizationVerificationResponse toResponse(HostOrganization organization) {
        List<OrganizationVerificationDocumentResponse> items = documents.findDocuments(organization.getId())
                .stream().map(this::toDocumentResponse).toList();
        return OrganizationVerificationResponse.builder().organizationId(organization.getId())
                .status(organization.getVerificationStatus()).submittedAt(organization.getVerificationSubmittedAt())
                .decidedAt(organization.getVerificationDecidedAt()).reason(organization.getVerificationReason())
                .documents(items).uploadHint(UPLOAD_HINT).build();
    }

    private OrganizationVerificationDocumentResponse toDocumentResponse(OrganizationVerificationDocument value) {
        return OrganizationVerificationDocumentResponse.builder().id(value.getId()).kind(value.getKind())
                .fileUrl(value.getFileUrl()).fileName(value.getFileName()).note(value.getNote()).status(value.getStatus())
                .reviewNote(value.getReviewNote()).reviewedAt(value.getReviewedAt()).createdAt(value.getCreatedAt()).build();
    }

    private VerificationQueueItemResponse toQueueItem(VerificationQueueEntry entry) {
        return VerificationQueueItemResponse.builder().organizationId(entry.getOrganizationId())
                .displayName(entry.getDisplayName()).legalName(entry.getLegalName())
                .organizationType(entry.getOrganizationType()).submittedAt(entry.getSubmittedAt())
                .documentCount(entry.getDocumentCount()).ownerName(entry.getOwnerName()).ownerEmail(entry.getOwnerEmail()).build();
    }

    private String safeFileName(String value) {
        if (value == null || value.isBlank()) return null;
        String name = value.replaceAll("[\\\\/]+", "_").trim();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
