package com.ds.goroute.quest.dto;

/**
 * @param accepted false when the phone was too far from the object; nothing was recorded.
 * @param cleared  true when this tap cleared the checkpoint.
 * @param run      the run afterwards, so the app moves on without another call.
 * @param object   what the object reveals (its story, photos, recording), once accepted. The run
 *                 has usually moved on to the next checkpoint by then, so it is sent here.
 */
public record QuestArTapResponse(boolean accepted, boolean cleared, QuestRunResponse run,
                                 QuestArObjectView object) {
}
