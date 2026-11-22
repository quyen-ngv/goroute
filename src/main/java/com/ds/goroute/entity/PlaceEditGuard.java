package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** What decides whether a place takes edit suggestions (PlaceMapper.findEditGuard). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditGuard {
    private UUID id;
    private String title;
    private String placeGroup;
    /** Google's business status text ("Permanently closed", "Temporarily closed", blank). */
    private String status;
    private String visibilityStatus;
    private String lifecycleStatus;
    /** Partner-sourced, with a partner source row, or with an enabled hotel profile. */
    private boolean partnerPlace;
}
