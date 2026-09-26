package com.ds.goroute.quest.dto;

import java.util.UUID;

/** The outcome of a review action: the quest's new status and whether it was self-approved. */
public record QuestReviewResultResponse(UUID questId, String status, boolean selfApproved) {
}
