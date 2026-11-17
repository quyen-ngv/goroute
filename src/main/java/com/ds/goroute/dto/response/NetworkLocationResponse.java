package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where the request's network is, roughly: the city of the client's IP address as
 * Cloudflare places it. Good for centring a list of suggestions when the device gives no
 * position, never for anything that decides on distance (verification, rewards). A mobile
 * network often exits in another city, and a roaming SIM in its home country.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkLocationResponse {
    private double latitude;
    private double longitude;

    /** City name as Cloudflare spells it (often without diacritics); null when unknown. */
    private String city;

    /** ISO 3166-1 alpha-2 country code; null when unknown. */
    private String countryCode;
}
