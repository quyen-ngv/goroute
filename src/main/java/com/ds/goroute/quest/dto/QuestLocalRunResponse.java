package com.ds.goroute.quest.dto;

import java.util.UUID;

/**
 * What the server made of an uploaded local run.
 *
 * @param completed    every checkpoint held up on replay, so the run counts as completed.
 * @param rewarded     completion Stars and the passport stamp were granted.
 * @param clearedCheckpoints how many checkpoints the replay accepted.
 * @param unpaidClues  clues used on the phone that could not be charged (not enough Stars): the run
 *                     is kept but not rewarded.
 */
public record QuestLocalRunResponse(UUID runId, String status, boolean completed, boolean rewarded,
                                    int clearedCheckpoints, int totalCheckpoints, int unpaidClues) {
}
