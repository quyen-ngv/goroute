package com.ds.goroute.quest.dto;

import java.util.List;
import java.util.UUID;

/** A player's answer to one question: free text / a number, or the chosen option ids. */
public record QuestAnswerRequest(String text, List<UUID> choiceIds) {
}
