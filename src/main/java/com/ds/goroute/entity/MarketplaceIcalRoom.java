package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Room type projection used by the iCal export: identity, owning hotel/organization and feed token. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketplaceIcalRoom {
    private UUID roomTypeId;
    private UUID hotelId;
    private UUID organizationId;
    private String roomName;
    private String hotelName;
    private String icalToken;
}
