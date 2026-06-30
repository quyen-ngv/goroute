package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.event.ContributionScrapePollEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceContributionMapper;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.PlaceContributionService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.thirdparty.scrape.*;
import com.ds.goroute.type.ContributionGroupStatus;
import com.ds.goroute.type.ContributionStatus;
import com.ds.goroute.utils.GoogleMapsUrlUtils;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceContributionServiceImpl implements PlaceContributionService {

    private static final List<ContributionGroupStatus> ACTIVE_GROUP_STATUSES = List.of(
            ContributionGroupStatus.PENDING,
            ContributionGroupStatus.APPROVED,
            ContributionGroupStatus.SCRAPING
    );

    private final PlaceContributionMapper contributionMapper;
    private final PlaceRepository placeRepository;
    private final PlaceService placeService;
    private final UserRepository userRepository;
    private final UserReviewRepository reviewRepository;
    private final UserReviewProfileRepository profileRepository;
    private final ReviewScoringService scoringService;
    private final ScrapeServiceClient scrapeServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${goroute.internal.public-base-url:http://goroute-app:8080}")
    private String publicBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public CheckContributionResponse checkContribution(CheckContributionRequest request) {
        String normalizedUrl = GoogleMapsUrlUtils.normalizeUrl(request.getGoogleMapsUrl());
        String urlHash = GoogleMapsUrlUtils.hashUrl(normalizedUrl);

        Place existingPlace = findExistingPlace(normalizedUrl, request.getGoogleMapsUrl());
        if (existingPlace != null) {
            return CheckContributionResponse.builder()
                    .exists(true)
                    .matchType("GOOGLE_PLACE_ID")
                    .existingPlace(toExistingPlaceSummary(existingPlace))
                    .canContributeReview(true)
                    .message("Place already exists on GoRoute. You can write a review directly on the place page.")
                    .build();
        }

        PlaceContributionGroup pendingGroup = contributionMapper.findActiveGroupByUrlHash(urlHash, ACTIVE_GROUP_STATUSES);
        if (pendingGroup != null) {
            return CheckContributionResponse.builder()
                    .exists(false)
                    .matchType("PENDING_CONTRIBUTION")
                    .pendingContributionGroupId(pendingGroup.getId())
                    .canContributeReview(true)
                    .message("Someone already submitted this place. You can join the contribution and add your review.")
                    .build();
        }

        return CheckContributionResponse.builder()
                .exists(false)
                .canContributeReview(true)
                .message("This place is not on GoRoute yet. You can contribute it.")
                .build();
    }

    @Override
    @Transactional
    public ContributionResponse createContribution(UUID userId, CreateContributionRequest request) {
        String normalizedUrl = GoogleMapsUrlUtils.normalizeUrl(request.getGoogleMapsUrl());
        String urlHash = GoogleMapsUrlUtils.hashUrl(normalizedUrl);

        Place existingPlace = findExistingPlace(normalizedUrl, request.getGoogleMapsUrl());
        if (existingPlace != null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "This place already exists. Please write your review on the place detail page.");
        }

        PlaceContributionGroup group = contributionMapper.findActiveGroupByUrlHash(urlHash, ACTIVE_GROUP_STATUSES);
        LocalDateTime now = LocalDateTime.now();
        if (group == null) {
            group = PlaceContributionGroup.builder()
                    .id(UUID.randomUUID())
                    .normalizedUrlHash(urlHash)
                    .googleMapsUrl(normalizedUrl)
                    .placeNameHint(request.getPlaceNameHint())
                    .resolvedGooglePlaceId(GoogleMapsUrlUtils.extractGooglePlaceId(normalizedUrl))
                    .status(ContributionGroupStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            contributionMapper.insertGroup(group);
        }

        PlaceContribution existingContribution =
                contributionMapper.findContributionByUserAndGroup(userId, group.getId());
        if (existingContribution != null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "You already submitted a contribution for this place.");
        }

        PlaceContribution contribution = PlaceContribution.builder()
                .id(UUID.randomUUID())
                .groupId(group.getId())
                .userId(userId)
                .googleMapsUrl(normalizedUrl)
                .placeNameHint(request.getPlaceNameHint())
                .status(ContributionStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        contributionMapper.insertContribution(contribution);

        PendingContributionReview pendingReview = PendingContributionReview.builder()
                .id(UUID.randomUUID())
                .contributionId(contribution.getId())
                .overallRating(request.getOverallRating())
                .foodRating(request.getFoodRating())
                .priceRating(request.getPriceRating())
                .ambianceRating(request.getAmbianceRating())
                .serviceRating(request.getServiceRating())
                .text(request.getText())
                .photos(request.getPhotos() != null ? JsonUtils.toJson(request.getPhotos()) : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        contributionMapper.insertPendingReview(pendingReview);

        return toContributionResponse(contribution, group, pendingReview);
    }

    @Override
    @Transactional
    public void cancelContribution(UUID userId, UUID contributionId) {
        PlaceContribution contribution = contributionMapper.findContributionById(contributionId);
        if (contribution == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Contribution not found");
        }
        if (!contribution.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                    "You can only cancel your own contribution");
        }
        if (contribution.getStatus() != ContributionStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Only pending contributions can be cancelled");
        }

        PlaceContributionGroup group = contributionMapper.findGroupById(contribution.getGroupId());
        if (group == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Contribution group not found");
        }
        if (group.getStatus() != ContributionGroupStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "This contribution can no longer be cancelled");
        }

        contributionMapper.deletePendingReviewByContributionId(contributionId);
        contributionMapper.deleteContributionById(contributionId);

        List<PlaceContribution> remaining =
                contributionMapper.findContributionsByGroupId(group.getId());
        if (remaining.isEmpty()) {
            contributionMapper.deleteGroupById(group.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContributionResponse> getMyContributions(UUID userId, int page, int size) {
        int offset = page * size;
        return contributionMapper.findContributionsByUserId(userId, size, offset).stream()
                .map(contribution -> {
                    PlaceContributionGroup group = contributionMapper.findGroupById(contribution.getGroupId());
                    PendingContributionReview pendingReview =
                            contributionMapper.findPendingReviewByContributionId(contribution.getId());
                    return toContributionResponse(contribution, group, pendingReview);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContributedPlaceResponse> getMyContributedPlaces(UUID userId, int page, int size) {
        int offset = page * size;
        List<PlaceContributor> contributors = contributionMapper.findContributorsByUserId(userId, size, offset);
        List<ContributedPlaceResponse> responses = new ArrayList<>();
        for (PlaceContributor contributor : contributors) {
            Place place = placeRepository.findById(contributor.getPlaceId()).orElse(null);
            if (place == null) {
                continue;
            }
            responses.add(ContributedPlaceResponse.builder()
                    .placeId(place.getId())
                    .placeGoogleId(place.getPlaceId())
                    .title(place.getTitle())
                    .address(place.getAddress())
                    .thumbnail(place.getThumbnail())
                    .contributionId(contributor.getContributionId())
                    .build());
        }
        return responses;
    }

    @Override
    @Transactional
    public ContributionImportResponse importContribution(ContributionImportRequest request) {
        UUID groupId = request.getContributionGroupId();
        UUID gorouteJobId = request.getGorouteJobId() != null ? request.getGorouteJobId() : UUID.fromString(request.getJobId());

        ContributionImportResponse existing = findExistingImportResult(groupId, gorouteJobId);
        if (existing != null) {
            existing.setCode("ALREADY_PROCESSED");
            return existing;
        }

        PlaceContributionGroup group = groupId != null ? contributionMapper.findGroupById(groupId) : null;

        boolean skipIfExists = request.getSkipPlaceInsertIfExists() == null || request.getSkipPlaceInsertIfExists();
        boolean placeAlreadyExists = Boolean.TRUE.equals(request.getPlaceAlreadyExists());
        Place place = null;
        ImportPlaceRequest placeRequest = request.getPlace();

        if (placeRequest != null && placeRequest.getPlaceId() != null) {
            place = placeRepository.findByPlaceId(placeRequest.getPlaceId());
            if (place != null) {
                placeAlreadyExists = true;
            }
        }

        if (place == null && !placeAlreadyExists && placeRequest != null) {
            PlaceResponse imported = placeService.importPlace(placeRequest);
            if (imported == null) {
                throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Failed to import place from scrape data");
            }
            place = placeRepository.findById(imported.getId())
                    .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Imported place not found"));
        } else         if (place == null && placeRequest != null && placeRequest.getPlaceId() != null) {
            place = placeRepository.findByPlaceId(placeRequest.getPlaceId());
            placeAlreadyExists = place != null;
        }

        if (place == null && request.getExistingGooglePlaceId() != null) {
            place = placeRepository.findByPlaceId(request.getExistingGooglePlaceId());
            placeAlreadyExists = place != null;
        }

        if (place == null && group != null && group.getResolvedGooglePlaceId() != null) {
            place = placeRepository.findByPlaceId(group.getResolvedGooglePlaceId());
            placeAlreadyExists = place != null;
        }

        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Unable to resolve place for contribution import");
        }

        if (placeAlreadyExists && skipIfExists && placeRequest != null) {
            log.info("Skipping place insert for existing google place id {}", place.getPlaceId());
        }

        List<GorouteContributionReviewInput> reviewInputs = resolveReviewInputs(request, group);
        int reviewsPublished = publishGorouteReviews(place.getId(), reviewInputs);
        int contributorsAdded = addContributors(place.getId(), request.getContributorUserIds(), group);

        if (group != null) {
            group.setLinkedPlaceId(place.getId());
            group.setResolvedGooglePlaceId(place.getPlaceId());
            group.setStatus(placeAlreadyExists
                    ? ContributionGroupStatus.MERGED_TO_EXISTING
                    : ContributionGroupStatus.COMPLETED);
            group.setUpdatedAt(LocalDateTime.now());
            contributionMapper.updateGroup(group);
            updateContributionsForGroup(group);
        }

        ContributionImportResponse response = ContributionImportResponse.builder()
                .goroutePlaceId(place.getId())
                .placeAlreadyExists(placeAlreadyExists)
                .reviewsPublished(reviewsPublished)
                .contributorsAdded(contributorsAdded)
                .build();

        saveImportLog(groupId, gorouteJobId, request.getJobId(), response);
        scoringService.recalculatePlaceScores(place.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ContributionImportResponse getImportResult(UUID contributionGroupId, UUID gorouteJobId) {
        ContributionImportResponse existing = findExistingImportResult(contributionGroupId, gorouteJobId);
        if (existing != null) {
            existing.setCode("ALREADY_PROCESSED");
        }
        return existing;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminContributionGroupResponse> adminListGroups(String status, int page, int size) {
        ContributionGroupStatus groupStatus = status != null && !status.isBlank()
                ? ContributionGroupStatus.valueOf(status.toUpperCase(Locale.ROOT))
                : ContributionGroupStatus.PENDING;
        int offset = page * size;
        return contributionMapper.findGroupsByStatus(groupStatus, size, offset).stream()
                .map(this::toAdminGroupResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminContributionGroupResponse adminGetGroup(UUID groupId) {
        PlaceContributionGroup group = contributionMapper.findGroupById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Contribution group not found");
        }
        return toAdminGroupResponse(group);
    }

    @Override
    @Transactional
    public void adminApprove(UUID groupId) {
        PlaceContributionGroup group = contributionMapper.findGroupById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Contribution group not found");
        }
        if (group.getStatus() != ContributionGroupStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Only pending contribution groups can be approved");
        }

        ScrapeResolveResponse resolved = scrapeServiceClient.resolveUrl(group.getGoogleMapsUrl());
        if (resolved != null && resolved.getGooglePlaceId() != null) {
            group.setResolvedGooglePlaceId(resolved.getGooglePlaceId());
        }

        Place existingPlace = findExistingPlace(group.getGoogleMapsUrl(), group.getGoogleMapsUrl());
        if (existingPlace == null && group.getResolvedGooglePlaceId() != null) {
            existingPlace = placeRepository.findByPlaceId(group.getResolvedGooglePlaceId());
        }

        if (existingPlace != null) {
            mergeGroupToExistingPlace(group, existingPlace);
            return;
        }

        UUID gorouteJobId = UUID.randomUUID();
        ScrapeContributionJobRequest scrapeRequest = buildScrapeRequest(group, gorouteJobId);
        ScrapeJobTriggerResponse trigger = scrapeServiceClient.triggerContributionScrape(scrapeRequest);
        if (trigger == null || trigger.getJobId() == null) {
            throw new BusinessException(ErrorConstant.HTTP_CONNECTION_ERROR,
                    "Failed to trigger scrape job — google-maps-bot is unavailable");
        }

        group.setGorouteJobId(gorouteJobId);
        group.setStatus(ContributionGroupStatus.SCRAPING);
        group.setScrapeJobId(trigger.getJobId());
        group.setUpdatedAt(LocalDateTime.now());
        contributionMapper.updateGroup(group);
        updateContributionsForGroup(group);

        eventPublisher.publishEvent(new ContributionScrapePollEvent(group.getId()));
    }

    @Override
    @Transactional
    public void adminReject(UUID groupId, String reason) {
        PlaceContributionGroup group = contributionMapper.findGroupById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Contribution group not found");
        }
        group.setStatus(ContributionGroupStatus.REJECTED);
        group.setRejectionReason(reason);
        group.setUpdatedAt(LocalDateTime.now());
        contributionMapper.updateGroup(group);

        ContributionStatus contributionStatus = ContributionStatus.REJECTED;
        for (PlaceContribution contribution : contributionMapper.findContributionsByGroupId(groupId)) {
            contribution.setStatus(contributionStatus);
            contribution.setUpdatedAt(LocalDateTime.now());
            contributionMapper.updateContribution(contribution);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContributorSummaryResponse> getPlaceContributors(UUID placeId) {
        return contributionMapper.findContributorsByPlaceId(placeId).stream()
                .map(contributor -> {
                    User user = userRepository.findById(contributor.getUserId()).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    return ContributorSummaryResponse.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName())
                            .username(user.getUsername())
                            .avatarUrl(user.getAvatarUrl())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void syncScrapingGroup(UUID groupId) {
        PlaceContributionGroup group = contributionMapper.findGroupById(groupId);
        if (group == null || group.getScrapeJobId() == null) {
            return;
        }
        ScrapeJobStatusResponse jobStatus = scrapeServiceClient.pollJob(group.getScrapeJobId());
        if (jobStatus == null) {
            return;
        }
        if ("completed".equalsIgnoreCase(jobStatus.getStatus())) {
            if (group.getStatus() != ContributionGroupStatus.COMPLETED
                    && group.getStatus() != ContributionGroupStatus.MERGED_TO_EXISTING) {
                if (jobStatus.getResult() != null && jobStatus.getResult().getGoroutePlaceId() != null) {
                    UUID placeId = UUID.fromString(jobStatus.getResult().getGoroutePlaceId());
                    group.setLinkedPlaceId(placeId);
                    group.setStatus(Boolean.TRUE.equals(jobStatus.getResult().getPlaceAlreadyExists())
                            ? ContributionGroupStatus.MERGED_TO_EXISTING
                            : ContributionGroupStatus.COMPLETED);
                    group.setUpdatedAt(LocalDateTime.now());
                    contributionMapper.updateGroup(group);
                    updateContributionsForGroup(group);
                }
            }
        } else if ("failed".equalsIgnoreCase(jobStatus.getStatus())) {
            group.setStatus(ContributionGroupStatus.FAILED);
            group.setUpdatedAt(LocalDateTime.now());
            contributionMapper.updateGroup(group);
            updateContributionsForGroup(group);
        }
    }

    private void mergeGroupToExistingPlace(PlaceContributionGroup group, Place existingPlace) {
        List<GorouteContributionReviewInput> reviews = loadPendingReviewInputs(group.getId());
        publishGorouteReviews(existingPlace.getId(), reviews);
        addContributors(existingPlace.getId(), null, group);
        scoringService.recalculatePlaceScores(existingPlace.getId());

        group.setLinkedPlaceId(existingPlace.getId());
        group.setResolvedGooglePlaceId(existingPlace.getPlaceId());
        group.setStatus(ContributionGroupStatus.MERGED_TO_EXISTING);
        group.setUpdatedAt(LocalDateTime.now());
        contributionMapper.updateGroup(group);
        updateContributionsForGroup(group);

        ContributionImportResponse response = ContributionImportResponse.builder()
                .goroutePlaceId(existingPlace.getId())
                .placeAlreadyExists(true)
                .reviewsPublished(reviews.size())
                .contributorsAdded(contributionMapper.findContributionsByGroupId(group.getId()).size())
                .build();
        saveImportLog(group.getId(), group.getGorouteJobId(), group.getScrapeJobId(), response);
    }

    private ScrapeContributionJobRequest buildScrapeRequest(PlaceContributionGroup group, UUID gorouteJobId) {
        List<PlaceContribution> contributions = contributionMapper.findContributionsByGroupId(group.getId());
        List<GorouteContributionReviewInput> reviewInputs = loadPendingReviewInputs(group.getId());
        List<UUID> contributorUserIds = contributions.stream()
                .map(PlaceContribution::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> headers = Map.of("Content-Type", "application/json");

        return ScrapeContributionJobRequest.builder()
                .url(group.getGoogleMapsUrl())
                .importConfig(ScrapeContributionJobRequest.ImportConfig.builder()
                        .enabled(true)
                        .url(publicBaseUrl + "/v1/api/internal/places/import/contribution")
                        .method("POST")
                        .headers(headers)
                        .build())
                .contribution(ScrapeContributionJobRequest.ContributionPayload.builder()
                        .gorouteJobId(gorouteJobId)
                        .contributionGroupId(group.getId())
                        .skipPlaceInsertIfExists(true)
                        .contributorUserIds(contributorUserIds)
                        .gorouteReviews(reviewInputs)
                        .build())
                .build();
    }

    private List<GorouteContributionReviewInput> loadPendingReviewInputs(UUID groupId) {
        List<PendingContributionReview> pendingReviews = contributionMapper.findPendingReviewsByGroupId(groupId);
        List<GorouteContributionReviewInput> inputs = new ArrayList<>();
        for (PendingContributionReview pendingReview : pendingReviews) {
            PlaceContribution contribution = contributionMapper.findContributionById(pendingReview.getContributionId());
            if (contribution == null) {
                continue;
            }
            inputs.add(GorouteContributionReviewInput.builder()
                    .contributionId(contribution.getId())
                    .userId(contribution.getUserId())
                    .overallRating(pendingReview.getOverallRating())
                    .foodRating(pendingReview.getFoodRating())
                    .priceRating(pendingReview.getPriceRating())
                    .ambianceRating(pendingReview.getAmbianceRating())
                    .serviceRating(pendingReview.getServiceRating())
                    .text(pendingReview.getText())
                    .photos(parsePhotos(pendingReview.getPhotos()))
                    .build());
        }
        return inputs;
    }

    private List<GorouteContributionReviewInput> resolveReviewInputs(
            ContributionImportRequest request,
            PlaceContributionGroup group) {
        if (request.getGorouteReviews() != null && !request.getGorouteReviews().isEmpty()) {
            return request.getGorouteReviews();
        }
        if (group != null) {
            return loadPendingReviewInputs(group.getId());
        }
        return List.of();
    }

    private int publishGorouteReviews(UUID placeId, List<GorouteContributionReviewInput> reviewInputs) {
        int published = 0;
        for (GorouteContributionReviewInput input : reviewInputs) {
            if (input.getUserId() == null || input.getOverallRating() == null) {
                continue;
            }
            Optional<UserReview> existing = reviewRepository.findByUserAndPlace(input.getUserId(), placeId);
            LocalDateTime now = LocalDateTime.now();
            if (existing.isPresent()) {
                UserReview review = existing.get();
                review.setOverallRating(input.getOverallRating());
                review.setFoodRating(input.getFoodRating());
                review.setPriceRating(input.getPriceRating());
                review.setAmbianceRating(input.getAmbianceRating());
                review.setServiceRating(input.getServiceRating());
                review.setText(input.getText());
                review.setPhotos(input.getPhotos() != null ? JsonUtils.toJson(input.getPhotos()) : null);
                review.setUpdatedAt(now);
                reviewRepository.update(review);
            } else {
                UserReview review = UserReview.builder()
                        .id(UUID.randomUUID())
                        .userId(input.getUserId())
                        .placeId(placeId)
                        .overallRating(input.getOverallRating())
                        .foodRating(input.getFoodRating())
                        .priceRating(input.getPriceRating())
                        .ambianceRating(input.getAmbianceRating())
                        .serviceRating(input.getServiceRating())
                        .text(input.getText())
                        .photos(input.getPhotos() != null ? JsonUtils.toJson(input.getPhotos()) : null)
                        .weight(BigDecimal.ONE)
                        .helpfulVotes(0)
                        .unhelpfulVotes(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                reviewRepository.save(review);
                profileRepository.incrementReviewCount(input.getUserId());
                scoringService.updateUserTier(input.getUserId());
            }
            published++;
        }
        return published;
    }

    private int addContributors(UUID placeId, List<UUID> contributorUserIds, PlaceContributionGroup group) {
        Set<UUID> userIds = new LinkedHashSet<>();
        if (contributorUserIds != null) {
            userIds.addAll(contributorUserIds);
        }
        if (group != null) {
            contributionMapper.findContributionsByGroupId(group.getId()).stream()
                    .map(PlaceContribution::getUserId)
                    .forEach(userIds::add);
        }

        int before = contributionMapper.findContributorsByPlaceId(placeId).size();
        LocalDateTime now = LocalDateTime.now();
        for (UUID userId : userIds) {
            UUID contributionId = null;
            if (group != null) {
                PlaceContribution contribution =
                        contributionMapper.findContributionByUserAndGroup(userId, group.getId());
                if (contribution != null) {
                    contributionId = contribution.getId();
                }
            }
            contributionMapper.insertContributor(PlaceContributor.builder()
                    .placeId(placeId)
                    .userId(userId)
                    .contributionId(contributionId)
                    .createdAt(now)
                    .build());
        }
        int after = contributionMapper.findContributorsByPlaceId(placeId).size();
        return Math.max(0, after - before);
    }

    private ContributionImportResponse findExistingImportResult(UUID groupId, UUID gorouteJobId) {
        PlaceContributionImportLog logEntry = null;
        if (gorouteJobId != null) {
            logEntry = contributionMapper.findImportLogByGorouteJobId(gorouteJobId);
        }
        if (logEntry == null && groupId != null) {
            logEntry = contributionMapper.findImportLogByGroupId(groupId);
        }
        if (logEntry == null) {
            return null;
        }
        return ContributionImportResponse.builder()
                .goroutePlaceId(logEntry.getGoroutePlaceId())
                .placeAlreadyExists(Boolean.TRUE.equals(logEntry.getPlaceAlreadyExists()))
                .reviewsPublished(logEntry.getReviewsPublished() != null ? logEntry.getReviewsPublished() : 0)
                .contributorsAdded(logEntry.getContributorsAdded() != null ? logEntry.getContributorsAdded() : 0)
                .build();
    }

    private void saveImportLog(UUID groupId, UUID gorouteJobId, String scrapeJobId, ContributionImportResponse response) {
        if (groupId == null) {
            return;
        }
        PlaceContributionImportLog logEntry = PlaceContributionImportLog.builder()
                .id(UUID.randomUUID())
                .contributionGroupId(groupId)
                .gorouteJobId(gorouteJobId)
                .scrapeJobId(scrapeJobId)
                .goroutePlaceId(response.getGoroutePlaceId())
                .placeAlreadyExists(response.isPlaceAlreadyExists())
                .reviewsPublished(response.getReviewsPublished())
                .contributorsAdded(response.getContributorsAdded())
                .processedAt(LocalDateTime.now())
                .build();
        contributionMapper.insertImportLog(logEntry);
    }

    private Place findExistingPlace(String normalizedUrl, String rawUrl) {
        String googlePlaceId = GoogleMapsUrlUtils.extractGooglePlaceId(rawUrl);
        if (googlePlaceId == null) {
            googlePlaceId = GoogleMapsUrlUtils.extractGooglePlaceId(normalizedUrl);
        }
        if (googlePlaceId != null) {
            Place byPlaceId = placeRepository.findByPlaceId(googlePlaceId);
            if (byPlaceId != null) {
                return byPlaceId;
            }
        }

        String cid = GoogleMapsUrlUtils.extractCid(rawUrl);
        if (cid != null) {
            Place byCid = contributionMapper.findPlaceByCid(cid);
            if (byCid != null) {
                return byCid;
            }
        }

        return contributionMapper.findPlaceByGoogleMapsLink(normalizedUrl);
    }

    private void updateContributionsForGroup(PlaceContributionGroup group) {
        ContributionStatus status = mapGroupStatusToContributionStatus(group.getStatus());
        for (PlaceContribution contribution : contributionMapper.findContributionsByGroupId(group.getId())) {
            contribution.setStatus(status);
            contribution.setUpdatedAt(LocalDateTime.now());
            contributionMapper.updateContribution(contribution);
        }
    }

    private ContributionStatus mapGroupStatusToContributionStatus(ContributionGroupStatus groupStatus) {
        return switch (groupStatus) {
            case PENDING -> ContributionStatus.PENDING;
            case APPROVED, SCRAPING -> ContributionStatus.SCRAPING;
            case COMPLETED -> ContributionStatus.COMPLETED;
            case MERGED_TO_EXISTING -> ContributionStatus.MERGED_TO_EXISTING;
            case REJECTED -> ContributionStatus.REJECTED;
            case FAILED -> ContributionStatus.FAILED;
        };
    }

    private AdminContributionGroupResponse toAdminGroupResponse(PlaceContributionGroup group) {
        List<PlaceContribution> contributions = contributionMapper.findContributionsByGroupId(group.getId());
        String linkedPlaceTitle = null;
        if (group.getLinkedPlaceId() != null) {
            linkedPlaceTitle = placeRepository.findById(group.getLinkedPlaceId())
                    .map(Place::getTitle)
                    .orElse(null);
        }

        List<AdminContributionItemResponse> items = contributions.stream()
                .map(contribution -> {
                    User user = userRepository.findById(contribution.getUserId()).orElse(null);
                    PendingContributionReview pendingReview =
                            contributionMapper.findPendingReviewByContributionId(contribution.getId());
                    return AdminContributionItemResponse.builder()
                            .id(contribution.getId())
                            .userId(contribution.getUserId())
                            .userName(user != null ? user.getFullName() : null)
                            .userAvatarUrl(user != null ? user.getAvatarUrl() : null)
                            .status(contribution.getStatus())
                            .pendingReview(toPendingReviewResponse(pendingReview))
                            .createdAt(contribution.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return AdminContributionGroupResponse.builder()
                .id(group.getId())
                .googleMapsUrl(group.getGoogleMapsUrl())
                .placeNameHint(group.getPlaceNameHint())
                .resolvedGooglePlaceId(group.getResolvedGooglePlaceId())
                .status(group.getStatus())
                .linkedPlaceId(group.getLinkedPlaceId())
                .linkedPlaceTitle(linkedPlaceTitle)
                .scrapeJobId(group.getScrapeJobId())
                .gorouteJobId(group.getGorouteJobId())
                .adminNote(group.getAdminNote())
                .rejectionReason(group.getRejectionReason())
                .contributorCount(contributions.size())
                .contributions(items)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private ContributionResponse toContributionResponse(
            PlaceContribution contribution,
            PlaceContributionGroup group,
            PendingContributionReview pendingReview) {
        String linkedPlaceTitle = null;
        if (group != null && group.getLinkedPlaceId() != null) {
            linkedPlaceTitle = placeRepository.findById(group.getLinkedPlaceId())
                    .map(Place::getTitle)
                    .orElse(null);
        }
        return ContributionResponse.builder()
                .id(contribution.getId())
                .groupId(contribution.getGroupId())
                .googleMapsUrl(contribution.getGoogleMapsUrl())
                .placeNameHint(contribution.getPlaceNameHint())
                .status(contribution.getStatus())
                .groupStatus(group != null ? group.getStatus().name() : null)
                .linkedPlaceId(group != null ? group.getLinkedPlaceId() : null)
                .linkedPlaceTitle(linkedPlaceTitle)
                .pendingReview(toPendingReviewResponse(pendingReview))
                .createdAt(contribution.getCreatedAt())
                .updatedAt(contribution.getUpdatedAt())
                .build();
    }

    private PendingContributionReviewResponse toPendingReviewResponse(PendingContributionReview pendingReview) {
        if (pendingReview == null) {
            return null;
        }
        return PendingContributionReviewResponse.builder()
                .id(pendingReview.getId())
                .overallRating(pendingReview.getOverallRating())
                .foodRating(pendingReview.getFoodRating())
                .priceRating(pendingReview.getPriceRating())
                .ambianceRating(pendingReview.getAmbianceRating())
                .serviceRating(pendingReview.getServiceRating())
                .text(pendingReview.getText())
                .photos(parsePhotos(pendingReview.getPhotos()))
                .build();
    }

    private ExistingPlaceSummary toExistingPlaceSummary(Place place) {
        return ExistingPlaceSummary.builder()
                .id(place.getId())
                .title(place.getTitle())
                .placeId(place.getPlaceId())
                .address(place.getAddress())
                .build();
    }

    private List<String> parsePhotos(String photosJson) {
        if (photosJson == null || photosJson.isBlank()) {
            return List.of();
        }
        List<String> photos = JsonUtils.fromJson(photosJson, new TypeReference<List<String>>() {});
        return photos != null ? photos : List.of();
    }
}
