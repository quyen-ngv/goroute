package com.ds.goroute.service;

import com.ds.goroute.dto.response.PassportSummaryResponse;
import com.ds.goroute.dto.response.ProvinceMapEntryResponse;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.entity.UserCheckin;

import java.util.List;
import java.util.UUID;

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
}
