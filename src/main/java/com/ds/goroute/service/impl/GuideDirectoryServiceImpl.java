package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpsertGuideProfileRequest;
import com.ds.goroute.dto.request.UpsertGuideServiceRequest;
import com.ds.goroute.dto.response.GuideProfileResponse;
import com.ds.goroute.dto.response.GuideServiceResponse;
import com.ds.goroute.entity.GuideAvailability;
import com.ds.goroute.entity.GuideProfile;
import com.ds.goroute.entity.GuideService;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.GuideMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.GuideDirectoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.GuideProfileStatus;
import com.ds.goroute.type.GuideServiceStatus;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuideDirectoryServiceImpl implements GuideDirectoryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String DEFAULT_CURRENCY = "VND";
    /** How long an identity document is kept before it is disposed of automatically. */
    private static final int DOCUMENT_RETENTION_DAYS = 180;

    private final GuideMapper guideMapper;
    private final NotificationService notificationService;
    private final BusinessConfigService config;

    // --- the guide's own profile ------------------------------------------------------

    @Override
    @Transactional
    public GuideProfileResponse createOrUpdateProfile(UUID userId, UpsertGuideProfileRequest request) {
        requireFeatureEnabled();
        LocalDateTime now = LocalDateTime.now();
        GuideProfile existing = guideMapper.findProfileByUser(userId);

        if (existing == null) {
            GuideProfile profile = GuideProfile.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .displayName(request.getDisplayName().trim())
                    .headline(request.getHeadline())
                    .bio(request.getBio())
                    .languages(JsonUtils.toJson(orEmpty(request.getLanguages())))
                    .areaProvinceCodes(JsonUtils.toJson(orEmpty(request.getAreaProvinceCodes())))
                    .yearsExperience(request.getYearsExperience())
                    .avatarUrl(request.getAvatarUrl())
                    .contactPhone(request.getContactPhone())
                    .contactEmail(request.getContactEmail())
                    .status(GuideProfileStatus.DRAFT)
                    .plan("FREE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            guideMapper.insertProfile(profile);
            return toOwnerResponse(profile);
        }

        existing.setDisplayName(request.getDisplayName().trim());
        existing.setHeadline(request.getHeadline());
        existing.setBio(request.getBio());
        existing.setLanguages(JsonUtils.toJson(orEmpty(request.getLanguages())));
        existing.setAreaProvinceCodes(JsonUtils.toJson(orEmpty(request.getAreaProvinceCodes())));
        existing.setYearsExperience(request.getYearsExperience());
        existing.setAvatarUrl(request.getAvatarUrl());
        existing.setContactPhone(request.getContactPhone());
        existing.setContactEmail(request.getContactEmail());
        existing.setUpdatedAt(now);
        guideMapper.updateProfile(existing);
        return toOwnerResponse(existing);
    }

    @Override
    @Transactional
    public GuideProfileResponse submitForVerification(UUID userId) {
        GuideProfile profile = requireOwnProfile(userId);
        if (guideMapper.submitProfile(profile.getId(), userId) != 1) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Only a draft or a rejected application can be submitted");
        }
        return myProfile(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public GuideProfileResponse myProfile(UUID userId) {
        return toOwnerResponse(requireOwnProfile(userId));
    }

    /**
     * Identity documents get a disposal date at the moment they are stored.
     *
     * <p>Holding somebody's papers is an obligation, not a convenience. A document with no
     * expiry quietly becomes a permanent liability, so the deletion date is set here rather
     * than left to a policy somebody has to remember.
     */
    @Override
    @Transactional
    public void attachIdentityDocument(UUID userId, String documentType, String fileUrl) {
        GuideProfile profile = requireOwnProfile(userId);
        guideMapper.insertIdentityDocument(UUID.randomUUID(), profile.getId(), documentType, fileUrl,
                LocalDateTime.now().plusDays(DOCUMENT_RETENTION_DAYS));
    }

    // --- services ---------------------------------------------------------------------

    @Override
    @Transactional
    public GuideServiceResponse createService(UUID userId, UpsertGuideServiceRequest request) {
        GuideProfile profile = requireApprovedProfile(userId);
        GuideServiceStatus status = request.getStatus() == null
                ? GuideServiceStatus.DRAFT
                : request.getStatus();
        if (status == GuideServiceStatus.LISTED) {
            requireServiceSlotAvailable(profile);
        }

        LocalDateTime now = LocalDateTime.now();
        GuideService service = GuideService.builder()
                .id(UUID.randomUUID())
                .guideId(profile.getId())
                .title(request.getTitle().trim())
                .summary(request.getSummary())
                .itinerary(request.getItinerary())
                .durationHours(request.getDurationHours())
                .maxGuests(request.getMaxGuests())
                .meetingPoint(request.getMeetingPoint())
                .provinceCode(request.getProvinceCode())
                .inclusions(JsonUtils.toJson(orEmpty(request.getInclusions())))
                .exclusions(JsonUtils.toJson(orEmpty(request.getExclusions())))
                .pricingMode(request.getPricingMode())
                .priceAmount(request.getPriceAmount())
                .currency(request.getCurrency() == null ? DEFAULT_CURRENCY : request.getCurrency())
                .advanceNoticeHours(request.getAdvanceNoticeHours() == null
                        ? 24 : request.getAdvanceNoticeHours())
                .status(status)
                .isPromoted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        guideMapper.insertService(service);
        return toServiceResponse(service, profile);
    }

    @Override
    @Transactional
    public GuideServiceResponse updateService(UUID userId, UUID serviceId,
                                              UpsertGuideServiceRequest request) {
        GuideProfile profile = requireApprovedProfile(userId);
        GuideService service = guideMapper.findServiceById(serviceId);
        if (service == null || !service.getGuideId().equals(profile.getId())) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Service not found");
        }
        GuideServiceStatus status = request.getStatus() == null ? service.getStatus() : request.getStatus();
        if (status == GuideServiceStatus.LISTED && service.getStatus() != GuideServiceStatus.LISTED) {
            requireServiceSlotAvailable(profile);
        }

        service.setTitle(request.getTitle().trim());
        service.setSummary(request.getSummary());
        service.setItinerary(request.getItinerary());
        service.setDurationHours(request.getDurationHours());
        service.setMaxGuests(request.getMaxGuests());
        service.setMeetingPoint(request.getMeetingPoint());
        service.setProvinceCode(request.getProvinceCode());
        service.setInclusions(JsonUtils.toJson(orEmpty(request.getInclusions())));
        service.setExclusions(JsonUtils.toJson(orEmpty(request.getExclusions())));
        service.setPricingMode(request.getPricingMode());
        // A new price applies to new bookings. Existing ones keep the amount they were
        // agreed at, because that value was copied onto the booking row.
        service.setPriceAmount(request.getPriceAmount());
        service.setCurrency(request.getCurrency() == null ? DEFAULT_CURRENCY : request.getCurrency());
        service.setAdvanceNoticeHours(request.getAdvanceNoticeHours() == null
                ? service.getAdvanceNoticeHours() : request.getAdvanceNoticeHours());
        service.setStatus(status);
        service.setUpdatedAt(LocalDateTime.now());

        guideMapper.updateService(service);
        return toServiceResponse(service, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideServiceResponse> myServices(UUID userId) {
        GuideProfile profile = requireOwnProfile(userId);
        return guideMapper.findServicesByGuide(profile.getId()).stream()
                .map(service -> toServiceResponse(service, profile))
                .toList();
    }

    // --- availability -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<GuideAvailability> availability(UUID userId, LocalDate from, LocalDate to) {
        GuideProfile profile = requireOwnProfile(userId);
        return guideMapper.findAvailability(profile.getId(), from, to);
    }

    @Override
    @Transactional
    public void setAvailability(UUID userId, LocalDate date, boolean blocked,
                                Integer maxGuests, String note) {
        GuideProfile profile = requireOwnProfile(userId);
        // Blocking a day only stops new bookings. A booking already agreed stays agreed;
        // the traveller has planned around it.
        guideMapper.upsertAvailability(GuideAvailability.builder()
                .id(UUID.randomUUID())
                .guideId(profile.getId())
                .availableDate(date)
                .isBlocked(blocked)
                .maxGuests(maxGuests)
                .note(note)
                .build());
    }

    // --- public directory -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<GuideServiceResponse> search(String provinceCode, String language, BigDecimal maxPrice,
                                             LocalDate date, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return guideMapper.searchServices(provinceCode, language, maxPrice, date,
                        safeSize, Math.max(0, page) * safeSize).stream()
                .map(service -> toServiceResponse(service, guideMapper.findProfileById(service.getGuideId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countSearch(String provinceCode, String language, BigDecimal maxPrice, LocalDate date) {
        return guideMapper.countSearchServices(provinceCode, language, maxPrice, date);
    }

    @Override
    @Transactional(readOnly = true)
    public GuideProfileResponse publicProfile(UUID guideId) {
        GuideProfile profile = guideMapper.findProfileById(guideId);
        if (profile == null || !profile.getStatus().isPubliclyVisible()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Guide not found");
        }
        return toPublicResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideServiceResponse> publicServices(UUID guideId) {
        GuideProfile profile = guideMapper.findProfileById(guideId);
        if (profile == null || !profile.getStatus().isPubliclyVisible()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Guide not found");
        }
        return guideMapper.findServicesByGuide(guideId).stream()
                .filter(service -> service.getStatus() == GuideServiceStatus.LISTED)
                .map(service -> toServiceResponse(service, profile))
                .toList();
    }

    @Override
    @Transactional
    public GuideServiceResponse publicService(UUID serviceId) {
        GuideService service = guideMapper.findServiceById(serviceId);
        if (service == null || service.getStatus() != GuideServiceStatus.LISTED) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Service not found");
        }
        GuideProfile profile = guideMapper.findProfileById(service.getGuideId());
        if (profile == null || !profile.getStatus().isPubliclyVisible()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Service not found");
        }
        // Counted here rather than reported by the guide, so the analytics in GUIDE-08 are
        // measurements and not claims.
        guideMapper.recordServiceView(serviceId, false);
        return toServiceResponse(service, profile);
    }

    // --- operator ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<GuideProfileResponse> verificationQueue(GuideProfileStatus status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return guideMapper.findProfileQueue(status == null ? null : status.name(),
                        safeSize, Math.max(0, page) * safeSize).stream()
                .map(this::toOwnerResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countVerificationQueue(GuideProfileStatus status) {
        return guideMapper.countProfileQueue(status == null ? null : status.name());
    }

    @Override
    @Transactional
    public GuideProfileResponse decide(UUID operatorId, UUID guideId, GuideProfileStatus status,
                                       String decisionNote, String informationRequested) {
        GuideProfile profile = guideMapper.findProfileById(guideId);
        if (profile == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Guide not found");
        }
        guideMapper.decideProfile(guideId, status.name(), operatorId, decisionNote, informationRequested);

        // Suspension hides the services. Confirmed bookings are deliberately untouched:
        // travellers have planned around them, and they need their own handling rather
        // than a silent mass cancellation.
        if (status == GuideProfileStatus.SUSPENDED) {
            guideMapper.pauseServicesOverLimit(guideId, 0);
        }
        notifyDecision(profile, status, decisionNote, informationRequested);

        return toOwnerResponse(guideMapper.findProfileById(guideId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> identityDocuments(UUID guideId) {
        return guideMapper.findIdentityDocuments(guideId);
    }

    // --- helpers -----------------------------------------------------------------------

    private void requireFeatureEnabled() {
        if (!config.getBoolean(BusinessConfigKey.GUIDE_ENABLED)) {
            throw new BusinessException(ErrorConstant.GUIDE_FEATURE_DISABLED,
                    "The guide marketplace is currently unavailable.");
        }
    }

    private GuideProfile requireOwnProfile(UUID userId) {
        GuideProfile profile = guideMapper.findProfileByUser(userId);
        if (profile == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "You do not have a guide profile yet");
        }
        return profile;
    }

    private GuideProfile requireApprovedProfile(UUID userId) {
        GuideProfile profile = requireOwnProfile(userId);
        if (!profile.getStatus().canPublishServices()) {
            throw new BusinessException(ErrorConstant.GUIDE_NOT_APPROVED,
                    "Only approved guides can publish services.");
        }
        return profile;
    }

    private void requireServiceSlotAvailable(GuideProfile profile) {
        if ("PREMIUM".equalsIgnoreCase(profile.getPlan())) {
            return;
        }
        int limit = config.getInt(BusinessConfigKey.GUIDE_FREE_SERVICE_LIMIT);
        if (guideMapper.countListedServices(profile.getId()) >= limit) {
            throw new BusinessException(ErrorConstant.GUIDE_SERVICE_LIMIT_REACHED,
                    "You have reached the number of published services included in your plan.");
        }
    }

    private void notifyDecision(GuideProfile profile, GuideProfileStatus status,
                                String decisionNote, String informationRequested) {
        try {
            notificationService.createNotification(
                    profile.getUserId(), null, NotificationType.ADMIN_MESSAGE,
                    switch (status) {
                        case APPROVED -> "Hồ sơ hướng dẫn viên đã được duyệt";
                        case REJECTED -> "Hồ sơ hướng dẫn viên chưa được duyệt";
                        case SUSPENDED -> "Hồ sơ hướng dẫn viên đã bị tạm đình chỉ";
                        case PENDING_VERIFICATION -> "Cần bổ sung hồ sơ";
                        case DRAFT -> "Hồ sơ đã chuyển về bản nháp";
                    },
                    informationRequested == null ? decisionNote : informationRequested,
                    Map.of("guideId", profile.getId().toString(), "status", status.name()),
                    profile.getDecidedBy());
        } catch (RuntimeException exception) {
            log.warn("Could not notify guide {} about the decision: {}",
                    profile.getUserId(), exception.getMessage(), exception);
        }
    }

    private GuideProfileResponse toOwnerResponse(GuideProfile profile) {
        return baseResponse(profile)
                .contactPhone(profile.getContactPhone())
                .contactEmail(profile.getContactEmail())
                .plan(profile.getPlan())
                .planExpiresAt(profile.getPlanExpiresAt())
                .decisionNote(profile.getDecisionNote())
                .informationRequested(profile.getInformationRequested())
                .submittedAt(profile.getSubmittedAt())
                .build();
    }

    /**
     * The public shape. Contact details, plan and decision notes are simply not populated,
     * rather than sent and hidden by the screen.
     */
    private GuideProfileResponse toPublicResponse(GuideProfile profile) {
        return baseResponse(profile).build();
    }

    private GuideProfileResponse.GuideProfileResponseBuilder baseResponse(GuideProfile profile) {
        int minimumReviews = config.getInt(BusinessConfigKey.GUIDE_MIN_REVIEWS_TO_SHOW_RATING);
        int ratingCount = profile.getRatingCount() == null ? 0 : profile.getRatingCount();

        return GuideProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .displayName(profile.getDisplayName())
                .headline(profile.getHeadline())
                .bio(profile.getBio())
                .languages(readList(profile.getLanguages()))
                .areaProvinceCodes(readList(profile.getAreaProvinceCodes()))
                .yearsExperience(profile.getYearsExperience())
                .avatarUrl(profile.getAvatarUrl())
                .status(profile.getStatus())
                .responseMinutesAvg(profile.getResponseMinutesAvg())
                .completedBookings(profile.getCompletedBookings())
                // A five-star average from one review says nothing; below the threshold no
                // number is shown at all rather than a misleading one.
                .ratingAverage(ratingCount >= minimumReviews ? profile.getRatingAverage() : null)
                .ratingCount(ratingCount)
                .createdAt(profile.getCreatedAt());
    }

    private GuideServiceResponse toServiceResponse(GuideService service, GuideProfile profile) {
        return GuideServiceResponse.builder()
                .id(service.getId())
                .guideId(service.getGuideId())
                .guideDisplayName(profile == null ? null : profile.getDisplayName())
                .guideAvatarUrl(profile == null ? null : profile.getAvatarUrl())
                .title(service.getTitle())
                .summary(service.getSummary())
                .itinerary(service.getItinerary())
                .durationHours(service.getDurationHours())
                .maxGuests(service.getMaxGuests())
                .meetingPoint(service.getMeetingPoint())
                .provinceCode(service.getProvinceCode())
                .inclusions(readList(service.getInclusions()))
                .exclusions(readList(service.getExclusions()))
                .pricingMode(service.getPricingMode())
                .priceAmount(service.getPriceAmount())
                .currency(service.getCurrency())
                .priceLabel(priceLabel(service))
                .advanceNoticeHours(service.getAdvanceNoticeHours())
                .status(service.getStatus())
                .promoted(Boolean.TRUE.equals(service.getIsPromoted()))
                .build();
    }

    /** Spelled out, because "500,000" alone is the ambiguity disputes start from. */
    private String priceLabel(GuideService service) {
        String suffix = service.getPricingMode() == com.ds.goroute.type.GuidePricingMode.PER_PERSON
                ? "/khách"
                : "/nhóm";
        return service.getPriceAmount().toPlainString() + " " + service.getCurrency() + suffix;
    }

    private List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> parsed = JsonUtils.fromJson(json, new TypeReference<>() {
        });
        return parsed == null ? List.of() : parsed;
    }
}
