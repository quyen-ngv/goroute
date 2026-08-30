package com.ds.goroute.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * One curated Location Image anchor shown on the user's Passport map.
 *
 * <p>A Passport is configured from Location Images, not from the national province
 * catalogue.  The visit state is calculated from the user's passport events within the
 * configured Location Image radius.
 */
@Data
@Builder
public class PassportLocationMapEntryResponse {
    private UUID id;
    private String name;
    private String address;
    private String imageUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean visited;
    private int eventCount;
}
