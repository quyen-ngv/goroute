package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A change Pengoo proposed for a trip, and what became of it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProposalResponse {
    private UUID id;
    private UUID conversationId;
    private UUID tripId;
    private String action;
    private String status;
    private String summary;
    private Integer dayNumber;
    private Integer toDayNumber;
    private String startTime;
    private String endTime;
    private UUID placeId;
    private String placeName;
    private UUID activityId;
    private String activityName;
    private String category;
    /** PREPEND_DAYS: how many days come before day 1. */
    private Integer days;
    /** REMOVE_DAYS: how many activities the removed days hold (they are deleted with them). */
    private Integer activityCount;
    /** Day-changing actions: the trip's dates once applied, as of when it was proposed. */
    private LocalDate newStartDate;
    private LocalDate newEndDate;
    /** EDIT_EXPENSE / REMOVE_EXPENSE: the expense, and its description (or category) when proposed. */
    private UUID expenseId;
    private String expenseName;
    /**
     * Trip-information actions: one line per field, {@code field}, {@code from} (absent when new),
     * {@code to} (absent for a removal) and {@code currency} for money; values are raw
     * (ISO dates, enum names, plain numbers) for the app to format.
     */
    private List<Map<String, String>> lines;
    /**
     * NEW_TRIP / PLAN_DAYS: the plan. For NEW_TRIP {@code tripId} is empty until applied, then the
     * trip it created.
     */
    private AssistantTripPlanResponse plan;
    private UUID resolvedByUserId;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
    private UUID resultActivityId;
    private String failureReason;
    /**
     * Whether the reader may apply or dismiss it, i.e. edit its trip. Set only on the per-reader
     * read: a Pengoo DM has no trip of its own, so the conversation cannot answer this.
     */
    private Boolean viewerCanEdit;
}
