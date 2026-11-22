package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * How many results of each kind match a Feed search, for the filter tabs. Counted in the index
 * before each result is checked against the database, so a few may not be shown.
 */
@Data
@Builder
public class SocialSearchCountsResponse {
    private int users;
    private int checkins;
    private int trips;
    private int places;
    private int stays;
    private int tours;
    private int events;
}
