package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One row the coordinate pass could not place, reduced to the text the name fallback
 * needs. Each target projects its own address/destination columns into {@code matchText}
 * so the fallback has a single shape to work with.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAreaCandidateRow {
    private UUID id;
    private String matchText;
}
