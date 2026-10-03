package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestListItem;
import com.ds.goroute.quest.dto.QuestPublicDetailResponse;
import com.ds.goroute.quest.dto.QuestPublicSummaryResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public quest discovery (§6.3). Every query goes through the shared {@code publicQuestGate}, and
 * every response is a public shape that carries no answers or hints (rules 5.2/5.3), and no
 * coordinates except the route a creator chose to show on the detail page.
 */
@Service
@RequiredArgsConstructor
public class QuestDiscoveryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final QuestRepository repository;
    private final MarketplaceJson json;
    private final QuestRunRepository runRepository;

    @Transactional(readOnly = true)
    public PageResponse<QuestPublicSummaryResponse> search(String provinceCode, String language, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        String province = blankToNull(provinceCode);
        String lang = blankToNull(language);
        List<QuestPublicSummaryResponse> items = repository
                .findPublicQuests(province, lang, safeSize, safePage * safeSize).stream()
                .map(this::toSummary)
                .toList();
        long total = repository.countPublicQuests(province, lang);
        return PageResponse.of(items, total, safePage, safeSize);
    }

    /**
     * The quest page. A quest that left discovery by being paused or archived is still shown, not
     * playable, to a viewer who was in a run of it, so their history does not lead to a dead end.
     * Everyone else gets the same 404 as for a quest that does not exist.
     */
    @Transactional(readOnly = true)
    public QuestPublicDetailResponse detail(UUID questId, UUID viewerUserId) {
        QuestListItem publicItem = repository.findPublicQuestDetail(questId).orElse(null);
        if (publicItem != null) {
            return toDetail(publicItem, QuestStatus.PUBLISHED.name(), true);
        }
        return pastPlayerStatus(questId, viewerUserId)
                .flatMap(status -> repository.findQuestDetailUngated(questId)
                        .map(item -> toDetail(item, status.name(), false)))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
    }

    private Optional<QuestStatus> pastPlayerStatus(UUID questId, UUID viewerUserId) {
        if (viewerUserId == null) {
            return Optional.empty();
        }
        return repository.findQuestById(questId)
                .filter(quest -> quest.getPublishedVersionId() != null)
                .map(quest -> quest.questStatus())
                .filter(QuestStatus::isViewableByPastPlayers)
                .filter(status -> runRepository.existsMemberRun(questId, viewerUserId));
    }

    private QuestPublicDetailResponse toDetail(QuestListItem item, String status, boolean playable) {
        return new QuestPublicDetailResponse(
                item.getQuestId(), item.getOrigin(), item.getTitle(), item.getSummary(), item.getDescription(),
                item.getCoverMediaId(), item.getCoverUrl(), item.getDifficulty(), item.getEstimatedMinutes(), item.getDistanceMeters(),
                nz(item.getPriceStars()), nz(item.getRewardStars()), item.getProvinceCode(), item.getWardCode(),
                item.getSafetyNotes(), json.readList(item.getAmenityTags(), String.class),
                json.readList(item.getPlayableMonths(), Integer.class), item.getRunExpiryHours(),
                nz(item.getCheckpointCount()), nz(item.getRequiredCheckinCount()),
                // D17: creators see who is playing; players are told so on the purchase and safety screens.
                true, item.getContentLanguage(), item.isRevealRoute(), route(item), status, playable);
    }

    /**
     * The whole route, when the creator shows it: a PIN checkpoint at its spot, an AREA checkpoint
     * at its offset search circle only (never the real spot). Empty when the route is kept hidden.
     */
    private List<QuestPublicDetailResponse.RouteStop> route(QuestListItem item) {
        if (!item.isRevealRoute() || item.getPublishedVersionId() == null) {
            return List.of();
        }
        return repository.loadVersionGraph(item.getPublishedVersionId())
                .map(v -> v.getCheckpoints().stream().map(QuestDiscoveryService::routeStop).toList())
                .orElse(List.of());
    }

    private static QuestPublicDetailResponse.RouteStop routeStop(QuestCheckpoint cp) {
        boolean area = cp.isArea();
        return new QuestPublicDetailResponse.RouteStop(
                cp.getSortOrder() == null ? 0 : cp.getSortOrder(), cp.getName(), cp.getCategory(),
                cp.find().name(),
                area ? cp.getSearchCenterLat() : cp.getLatitude(),
                area ? cp.getSearchCenterLng() : cp.getLongitude(),
                area ? cp.getSearchRadiusM() : cp.getRadiusM());
    }

    private QuestPublicSummaryResponse toSummary(QuestListItem item) {
        return new QuestPublicSummaryResponse(
                item.getQuestId(), item.getOrigin(), item.getTitle(), item.getSummary(), item.getCoverMediaId(),
                item.getCoverUrl(), item.getDifficulty(), item.getEstimatedMinutes(), item.getDistanceMeters(),
                nz(item.getPriceStars()), nz(item.getRewardStars()), item.getProvinceCode(), item.getWardCode(),
                nz(item.getCheckpointCount()), json.readList(item.getAmenityTags(), String.class),
                item.getContentLanguage());
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
