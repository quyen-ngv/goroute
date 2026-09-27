package com.ds.goroute.quest.dto;

/** The member's record of one storytelling point, and how many of the checkpoint's now count. */
public record QuestStopVisitResponse(boolean visited, boolean countsTowardCompletion, int stopsCounted) {
}
