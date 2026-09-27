package com.ds.goroute.quest.dto;

/**
 * @param accepted false when the phone was too far from the object; nothing was recorded.
 * @param cleared  true when this tap cleared the checkpoint.
 * @param run      the run afterwards, so the app moves on without another call.
 */
public record QuestArTapResponse(boolean accepted, boolean cleared, QuestRunResponse run) {
}
