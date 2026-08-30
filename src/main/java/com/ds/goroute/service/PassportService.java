package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpsertPassportDefinitionRequest;
import com.ds.goroute.dto.request.UpsertPassportTagRequest;
import com.ds.goroute.dto.request.UpsertPassportRewardRequest;
import com.ds.goroute.dto.request.UpsertPassportStampRuleRequest;
import com.ds.goroute.dto.response.PassportDefinitionResponse;
import com.ds.goroute.dto.response.PassportProvinceOptionResponse;
import com.ds.goroute.dto.response.PassportSummaryResponse;
import com.ds.goroute.dto.response.PassportTagResponse;
import com.ds.goroute.dto.response.PlacePassportTagsResponse;
import com.ds.goroute.dto.response.PassportRewardResponse;
import com.ds.goroute.dto.response.PassportStampRuleResponse;
import com.ds.goroute.dto.response.ProvinceMapEntryResponse;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.entity.UserCheckin;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Passport: what somebody has seen, and what they earned for it (epic 04). */
public interface PassportService {

    /**
     * Records the passport consequence of a check-in and re-evaluates the stamp rules.
     * Safe to call again for the same check-in: the second call produces nothing.
     */
    void recordCheckin(UserCheckin checkin);

    /**
     * Brings activity check-ins that predate the passport into the event stream. Runs in
     * batches and is safe to interrupt and restart, because somebody who has used the app
     * for a year should not open the passport and find it empty.
     *
     * @return how many events were created
     */
    int backfillLegacyActivityCheckins(int batchSize);

    PassportSummaryResponse summary(UUID userId);

    List<ProvinceMapEntryResponse> provinceMap(UUID userId);

    List<PassportEvent> timeline(UUID userId, int page, int size);

    /** Events in one province, for the detail view behind a tap on the map. */
    List<PassportEvent> province(UUID userId, String provinceCode, int limit);

    void setEventHidden(UUID userId, UUID eventId, boolean hidden);

    void addProvinceWish(UUID userId, String provinceCode);

    void removeProvinceWish(UUID userId, String provinceCode);

    /** Operator-managed collections and tags. Award history remains immutable on updates. */
    List<PassportDefinitionResponse> passportDefinitions(boolean includeInactive);

    PassportDefinitionResponse createPassportDefinition(UpsertPassportDefinitionRequest request);

    PassportDefinitionResponse updatePassportDefinition(UUID id, UpsertPassportDefinitionRequest request);

    List<PassportProvinceOptionResponse> provinceOptions();

    List<PassportTagResponse> passportTags(boolean includeInactive);

    PlacePassportTagsResponse placePassportTags(UUID userId, UUID placeId);

    PassportTagResponse createPassportTag(UpsertPassportTagRequest request);

    PassportTagResponse updatePassportTag(UUID id, UpsertPassportTagRequest request);

    List<PassportStampRuleResponse> passportStampRules(boolean includeInactive);

    PassportStampRuleResponse createPassportStampRule(UpsertPassportStampRuleRequest request);

    PassportStampRuleResponse updatePassportStampRule(String code, int version,
                                                       UpsertPassportStampRuleRequest request);

    List<PassportRewardResponse> passportRewards(boolean includeInactive);

    PassportRewardResponse createPassportReward(UpsertPassportRewardRequest request);

    PassportRewardResponse updatePassportReward(UUID id, UpsertPassportRewardRequest request);

    /** Curated admin image upload, routed through the shared moderation/upload door. */
    String uploadCatalogImage(MultipartFile file);
}
