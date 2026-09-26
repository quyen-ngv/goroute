package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestListItem;
import com.ds.goroute.quest.dto.QuestPublicDetailResponse;
import com.ds.goroute.quest.dto.QuestPublicSummaryResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Public quest discovery (§6.3). Every query goes through the shared {@code publicQuestGate}, and
 * every response is a public shape that carries no coordinates, answers or hints (rules 5.2/5.3).
 */
@Service
@RequiredArgsConstructor
public class QuestDiscoveryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final QuestRepository repository;
    private final MarketplaceJson json;

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

    @Transactional(readOnly = true)
    public QuestPublicDetailResponse detail(UUID questId) {
        QuestListItem item = repository.findPublicQuestDetail(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        return new QuestPublicDetailResponse(
                item.getQuestId(), item.getOrigin(), item.getTitle(), item.getSummary(), item.getDescription(),
                item.getCoverMediaId(), item.getDifficulty(), item.getEstimatedMinutes(), item.getDistanceMeters(),
                nz(item.getPriceStars()), nz(item.getRewardStars()), item.getProvinceCode(), item.getWardCode(),
                item.getSafetyNotes(), json.readList(item.getAmenityTags(), String.class),
                json.readList(item.getPlayableMonths(), Integer.class), item.getRunExpiryHours(),
                nz(item.getCheckpointCount()), nz(item.getRequiredCheckinCount()),
                // D17: creators see who is playing; players are told so on the purchase and safety screens.
                true);
    }

    private QuestPublicSummaryResponse toSummary(QuestListItem item) {
        return new QuestPublicSummaryResponse(
                item.getQuestId(), item.getOrigin(), item.getTitle(), item.getSummary(), item.getCoverMediaId(),
                item.getDifficulty(), item.getEstimatedMinutes(), item.getDistanceMeters(),
                nz(item.getPriceStars()), nz(item.getRewardStars()), item.getProvinceCode(), item.getWardCode(),
                nz(item.getCheckpointCount()), json.readList(item.getAmenityTags(), String.class));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
