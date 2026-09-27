package com.ds.goroute.quest.dto;

/**
 * The result of one answer attempt (§3.12). Never carries the correct answer — a wrong guess just
 * says wrong, with how many attempts remain before the question locks.
 */
public record QuestAnswerResponse(
        boolean correct,
        int guessCount,
        int maxGuesses,
        boolean attemptsExhausted,
        boolean checkpointCleared) {
}
