package com.ds.goroute.quest.dto;

import java.util.UUID;

/** The result of unlocking a quest: what the player now owns and how it was paid for. */
public record QuestEntitlementResponse(UUID questId, String fundingSource) {
}
