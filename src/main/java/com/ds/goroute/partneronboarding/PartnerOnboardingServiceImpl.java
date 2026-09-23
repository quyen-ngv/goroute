package com.ds.goroute.partneronboarding;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.partneronboarding.domain.DraftData;
import com.ds.goroute.partneronboarding.domain.DraftStatus;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import com.ds.goroute.partneronboarding.domain.OnboardingDraft;
import com.ds.goroute.partneronboarding.domain.OnboardingSteps;
import com.ds.goroute.partneronboarding.dto.CreateDraftRequest;
import com.ds.goroute.partneronboarding.dto.DraftResponse;
import com.ds.goroute.partneronboarding.dto.DraftSummaryResponse;
import com.ds.goroute.partneronboarding.dto.OnboardingMeResponse;
import com.ds.goroute.partneronboarding.dto.OnboardingOrganizationResponse;
import com.ds.goroute.partneronboarding.dto.SaveStepRequest;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.partneronboarding.persistence.OnboardingDraftRepository;
import com.ds.goroute.partneronboarding.submit.ListingMaterializer;
import com.ds.goroute.partneronboarding.submit.ListingMaterializers;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerOnboardingServiceImpl implements PartnerOnboardingService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String HISTORY_ENTITY = "ONBOARDING_DRAFT";

    private final OnboardingDraftRepository drafts;
    private final HostOrganizationService organizations;
    private final PartnerAuthorizationService authorization;
    private final BusinessConfigService businessConfig;
    private final BetaAccessService betaAccess;
    private final FileUploadService fileUploadService;
    private final MarketplaceHistoryService historyService;
    private final ListingMaterializers materializers;
    private final MarketplaceJson json;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    // --- entry point --------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public OnboardingMeResponse me(UUID actorUserId) {
        List<HostOrganizationResponse> mine = organizations.listMine(actorUserId);
        return OnboardingMeResponse.builder()
                // Reported as the caller will actually experience them, beta list included,
                // so the app has one rule to follow and cannot drift from the one enforced below.
                .enabled(onboardingOpenTo(actorUserId))
                .partnerAppEnabled(businessConfig.getBoolean(BusinessConfigKey.PARTNER_APP_ENABLED)
                        || betaAccess.isBetaUser(actorUserId))
                .organizations(mine.stream().map(organization -> toOrganization(organization, actorUserId)).toList())
                .drafts(drafts.findByUser(actorUserId, DraftStatus.DRAFT, MAX_PAGE_SIZE, 0).stream()
                        .map(this::toSummary).toList())
                .maxActiveDrafts(maxActiveDrafts())
                .build();
    }

    // --- draft lifecycle ----------------------------------------------------------------

    @Override
    @Transactional
    public DraftResponse create(UUID actorUserId, CreateDraftRequest request) {
        requireFeatureEnabled(actorUserId);
        ListingKind kind = request.getListingKind();
        long active = drafts.countByUser(actorUserId, DraftStatus.DRAFT);
        if (active >= maxActiveDrafts()) {
            throw new BusinessException(ErrorConstant.ONBOARDING_TOO_MANY_DRAFTS);
        }
        if (request.getOrganizationId() != null) {
            // Checked now rather than at submit: a partner must not fill in ten screens for a
            // business they are not allowed to list under.
            authorization.requirePermission(request.getOrganizationId(), actorUserId, kind.writePermission());
        }
        LocalDateTime now = LocalDateTime.now();
        OnboardingDraft draft = OnboardingDraft.builder()
                .id(UUID.randomUUID())
                .userId(actorUserId)
                .organizationId(request.getOrganizationId())
                .listingKind(kind.name())
                .status(DraftStatus.DRAFT.name())
                .completedSteps(json.write(List.of()))
                .data(json.write(Map.of()))
                .dataVersion(1L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        drafts.insert(draft);
        return toResponse(actorUserId, draft);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DraftSummaryResponse> list(UUID actorUserId, DraftStatus status, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        List<DraftSummaryResponse> items = drafts
                .findByUser(actorUserId, status, safeSize, safePage * safeSize).stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.of(items, drafts.countByUser(actorUserId, status), safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public DraftResponse get(UUID actorUserId, UUID draftId) {
        return toResponse(actorUserId, readable(actorUserId, draftId));
    }

    @Override
    @Transactional
    public DraftResponse saveStep(UUID actorUserId, UUID draftId, String stepCode, SaveStepRequest request) {
        if (!OnboardingSteps.isKnown(stepCode)) {
            throw new BusinessException(ErrorConstant.ONBOARDING_STEP_UNKNOWN, "Unknown step: " + stepCode);
        }
        OnboardingDraft draft = editable(actorUserId, draftId);
        long version = expectedVersionOf(draft, request.getExpectedVersion());

        if (OnboardingSteps.ORGANIZATION.equals(stepCode)) {
            // Has a side effect the other steps do not: it produces the business every later
            // call authorizes against, so it runs before the answers are stored.
            version = attachOrganization(actorUserId, draft, request.getData(), version);
        }

        Map<String, Object> data = new LinkedHashMap<>(json.readMap(draft.getData()));
        data.put(stepCode, request.getData() == null ? Map.of() : request.getData());

        List<String> completed = new ArrayList<>(json.readList(draft.getCompletedSteps(), String.class));
        if (!completed.contains(stepCode)) {
            completed.add(stepCode);
        }

        if (!drafts.updateStep(draftId, version, stepCode, json.write(completed), json.write(data), LocalDateTime.now())) {
            throw conflictOrStale(draftId);
        }
        return toResponse(actorUserId, reload(draftId));
    }

    /**
     * Either attaches the business the partner picked, or creates the one they described.
     *
     * <p>Creating it here rather than at submit is what lets the wizard upload photos and
     * collect verification documents on the next screen: both are partner endpoints and both
     * need an organization to hang off.
     */
    private long attachOrganization(UUID actorUserId, OnboardingDraft draft, Map<String, Object> data, long version) {
        ListingKind kind = draft.kind();
        UUID chosen = readOrganizationId(data);
        if (chosen == null && draft.getOrganizationId() != null) {
            return version;
        }
        UUID organizationId;
        if (chosen != null) {
            authorization.requirePermission(chosen, actorUserId, kind.writePermission());
            organizationId = chosen;
        } else {
            organizationId = organizations.create(actorUserId, toCreateOrganizationRequest(data)).getId();
        }
        if (!drafts.updateOrganization(draft.getId(), version, organizationId, LocalDateTime.now())) {
            throw conflictOrStale(draft.getId());
        }
        draft.setOrganizationId(organizationId);
        return version + 1;
    }

    private UUID readOrganizationId(Map<String, Object> data) {
        Object value = data == null ? null : data.get("organizationId");
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "organizationId is not a valid id");
        }
    }

    /**
     * The step sends the business form as a loose map, so the constraints that protect the
     * organization table have to be applied by hand — Bean Validation only runs on a request
     * body the controller bound, and this one is nested inside another.
     */
    private CreateHostOrganizationRequest toCreateOrganizationRequest(Map<String, Object> data) {
        CreateHostOrganizationRequest request;
        try {
            request = objectMapper.convertValue(data == null ? Map.of() : data, CreateHostOrganizationRequest.class);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Business details are not in the expected shape");
        }
        Set<ConstraintViolation<CreateHostOrganizationRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, detail);
        }
        return request;
    }

    // --- media --------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ImageUploadOutcome> uploadMedia(UUID actorUserId, UUID draftId, List<MultipartFile> files) {
        OnboardingDraft draft = editable(actorUserId, draftId);
        UUID organizationId = requireOrganization(draft);
        ListingKind kind = draft.kind();
        authorization.requirePermission(organizationId, actorUserId, kind.writePermission());
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "At least one file is required");
        }
        ImageUploadRequest upload = ImageUploadRequest.of(actorUserId, kind.imageEntryPoint(),
                "partners/onboarding/" + organizationId + "/" + draftId);
        return fileUploadService.uploadImages(upload, files);
    }

    // --- submit -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubmitResultResponse submit(UUID actorUserId, UUID draftId, Long expectedVersion) {
        OnboardingDraft draft = readable(actorUserId, draftId);
        if (draft.draftStatus() == DraftStatus.SUBMITTED) {
            // Not an error the partner can fix — tell the client what already exists so it can
            // open the listing instead of building a second one.
            throw new BusinessException(ErrorConstant.ONBOARDING_ALREADY_SUBMITTED,
                    "Draft already produced listing " + firstNonNull(draft.getResultHotelId(), draft.getResultActivityId()));
        }
        requireEditableStatus(draft);
        UUID organizationId = requireOrganization(draft);
        ListingKind kind = draft.kind();
        var organization = authorization.requirePermission(organizationId, actorUserId, kind.writePermission());
        long version = expectedVersionOf(draft, expectedVersion);

        ListingMaterializer.Context context = new ListingMaterializer.Context(
                actorUserId, draftId, organizationId,
                organization.getTimezone(), organization.getDefaultCurrency(),
                DraftData.of(json.readMap(draft.getData())));
        SubmitResultResponse result = materializers.of(kind).materialize(context);

        LocalDateTime now = LocalDateTime.now();
        if (!drafts.updateStatus(draftId, version, DraftStatus.SUBMITTED,
                result.getHotelId(), result.getActivityId(), now, now)) {
            throw conflictOrStale(draftId);
        }
        historyService.audit(organizationId, HISTORY_ENTITY, draftId, "SUBMITTED",
                actorUserId, "USER", null, Map.of(
                        "listingKind", kind.name(),
                        "listingId", String.valueOf(result.listingId())));
        return result;
    }

    @Override
    @Transactional
    public void abandon(UUID actorUserId, UUID draftId, Long expectedVersion) {
        OnboardingDraft draft = editable(actorUserId, draftId);
        long version = expectedVersionOf(draft, expectedVersion);
        if (!drafts.updateStatus(draftId, version, DraftStatus.ABANDONED, null, null, null, LocalDateTime.now())) {
            throw conflictOrStale(draftId);
        }
    }

    // --- access -------------------------------------------------------------------------

    /**
     * A draft is the author's, plus anyone who could list for the same business.
     *
     * <p>Someone else's draft answers 404 rather than 403: a guessed id must not confirm that
     * a draft exists, and to a caller who may not see it the two are the same thing.
     */
    private OnboardingDraft readable(UUID actorUserId, UUID draftId) {
        OnboardingDraft draft = drafts.findById(draftId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.ONBOARDING_DRAFT_NOT_FOUND));
        if (actorUserId.equals(draft.getUserId())) {
            return draft;
        }
        UUID organizationId = draft.getOrganizationId();
        if (organizationId != null
                && authorization.hasPermission(organizationId, actorUserId, draft.kind().writePermission())) {
            return draft;
        }
        throw new BusinessException(ErrorConstant.ONBOARDING_DRAFT_NOT_FOUND);
    }

    private OnboardingDraft editable(UUID actorUserId, UUID draftId) {
        OnboardingDraft draft = readable(actorUserId, draftId);
        requireEditableStatus(draft);
        return draft;
    }

    private void requireEditableStatus(OnboardingDraft draft) {
        if (!draft.draftStatus().isEditable()) {
            throw new BusinessException(ErrorConstant.ONBOARDING_ALREADY_SUBMITTED,
                    "This draft is " + draft.getStatus().toLowerCase() + " and can no longer be changed");
        }
    }

    private UUID requireOrganization(OnboardingDraft draft) {
        UUID organizationId = draft.getOrganizationId();
        if (organizationId == null) {
            throw new BusinessException(ErrorConstant.ONBOARDING_ORGANIZATION_REQUIRED);
        }
        return organizationId;
    }

    private void requireFeatureEnabled(UUID actorUserId) {
        if (!onboardingOpenTo(actorUserId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Listing sign-up is not open yet");
        }
    }

    /**
     * Open to everyone once the flag is on, and to the beta list before that. Beta testers
     * are how a feature gets its first real use, so shipping one switched off has to still
     * leave them a way in.
     */
    private boolean onboardingOpenTo(UUID actorUserId) {
        return businessConfig.getBoolean(BusinessConfigKey.PARTNER_ONBOARDING_ENABLED)
                || betaAccess.isBetaUser(actorUserId);
    }

    private OnboardingDraft reload(UUID draftId) {
        return drafts.findById(draftId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.ONBOARDING_DRAFT_NOT_FOUND));
    }

    /**
     * A client that sends no version is taken at its word that it is the only editor — the
     * common case is one wizard on one device, and demanding a version there buys nothing.
     */
    private long expectedVersionOf(OnboardingDraft draft, Long requested) {
        return requested == null ? draft.getDataVersion() : requested;
    }

    private BusinessException conflictOrStale(UUID draftId) {
        log.debug("Onboarding draft {} changed under a concurrent write", draftId);
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "This draft was changed somewhere else. Reload it and try again.");
    }

    private int maxActiveDrafts() {
        return businessConfig.getInt(BusinessConfigKey.PARTNER_ONBOARDING_MAX_DRAFTS);
    }

    // --- mapping ------------------------------------------------------------------------

    private DraftResponse toResponse(UUID actorUserId, OnboardingDraft draft) {
        return DraftResponse.builder()
                .id(draft.getId())
                .listingKind(draft.kind())
                .status(draft.getStatus())
                .organizationId(draft.getOrganizationId())
                .organization(organizationOf(actorUserId, draft))
                .currentStep(draft.getCurrentStep())
                .completedSteps(json.readList(draft.getCompletedSteps(), String.class))
                .data(json.readMap(draft.getData()))
                .resultHotelId(draft.getResultHotelId())
                .resultActivityId(draft.getResultActivityId())
                .submittedAt(draft.getSubmittedAt())
                .expectedVersion(draft.getDataVersion())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    /**
     * The business summary that heads the wizard, read as the caller — not as the author, who
     * may not be the one looking and may since have lost access.
     *
     * <p>Decoration rather than payload: a draft must still open when the summary cannot be
     * read, so a failure here is logged and dropped instead of failing the whole response.
     */
    private OnboardingOrganizationResponse organizationOf(UUID actorUserId, OnboardingDraft draft) {
        UUID organizationId = draft.getOrganizationId();
        if (organizationId == null) {
            return null;
        }
        try {
            HostOrganizationResponse organization = organizations.getMine(actorUserId, organizationId);
            return organization == null ? null : toOrganization(organization, actorUserId);
        } catch (RuntimeException exception) {
            log.debug("Could not summarise organization {} for {}: {}",
                    organizationId, actorUserId, exception.getMessage());
            return null;
        }
    }

    private OnboardingOrganizationResponse toOrganization(HostOrganizationResponse organization, UUID actorUserId) {
        return OnboardingOrganizationResponse.builder()
                .id(organization.getId())
                .displayName(organization.getDisplayName())
                .verificationStatus(organization.getVerificationStatus())
                .operationalStatus(organization.getOperationalStatus())
                .owner(actorUserId.equals(organization.getOwnerUserId()))
                .build();
    }

    private DraftSummaryResponse toSummary(OnboardingDraft draft) {
        return DraftSummaryResponse.builder()
                .id(draft.getId())
                .listingKind(draft.kind())
                .status(draft.getStatus())
                .organizationId(draft.getOrganizationId())
                .currentStep(draft.getCurrentStep())
                .completedStepCount(json.readList(draft.getCompletedSteps(), String.class).size())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private static UUID firstNonNull(UUID first, UUID second) {
        return first != null ? first : second;
    }
}
