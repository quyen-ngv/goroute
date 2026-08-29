package com.ds.goroute.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * One province on the map.
 *
 * <p>{@code visited} and {@code wished} are separate fields and are never combined into a
 * single state, because marking somewhere as somewhere you want to go must never make the
 * "provinces visited" number go up.
 */
@Data
@Builder
public class ProvinceMapEntryResponse {
    private String code;
    private String name;
    private String region;
    /** From the official province list, so the app never hard-codes coordinates. */
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean visited;
    private boolean wished;
    private int eventCount;
}
