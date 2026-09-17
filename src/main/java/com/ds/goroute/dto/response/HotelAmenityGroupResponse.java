package com.ds.goroute.dto.response;

import java.util.List;

/**
 * Amenities of one section, in stored order. {@code amenities} holds {@link com.ds.goroute.type.HotelAmenity}
 * codes, except in the {@code OTHER} group, which holds the partner's free text as written.
 */
public record HotelAmenityGroupResponse(String group, List<String> amenities) {
}
