package com.ds.goroute.service;

import com.ds.goroute.dto.response.LocationAreaAutoMapResponse;
import com.ds.goroute.dto.response.LocationAreaCoverageResponse;
import com.ds.goroute.enums.LocationAreaTarget;

import java.util.UUID;

public interface LocationAreaService {

    /** Per-table mapped/unmapped counts. */
    LocationAreaCoverageResponse coverage();

    /**
     * Resolves a tourist area for every row that has none.
     *
     * @param dryRun when true the work is executed and then rolled back, so the caller
     *               sees the real numbers without changing any row
     */
    LocationAreaAutoMapResponse autoMap(boolean dryRun);

    /**
     * Sets the tourist area of a single row by hand, overriding whatever the auto-map job
     * resolved. This is the only write path an operator has: every entity's own update
     * endpoint leaves {@code location_image_id} alone, so a partner edit or a re-import can
     * never wipe the choice made here.
     *
     * @param locationImageId the area, or null to leave the row unmapped
     */
    void assign(LocationAreaTarget target, UUID id, UUID locationImageId);
}
