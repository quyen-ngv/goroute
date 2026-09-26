package com.ds.goroute.quest.dto;

/**
 * Starts a new draft quest. Nothing free-text here — the title and the rest arrive through the
 * first {@code PUT /draft}, which is where moderation runs.
 *
 * @param origin          honoured only on the console SYSTEM path; the app always creates COMMUNITY.
 * @param contentLanguage the language the content will be written in (default "vi").
 */
public record CreateQuestRequest(String origin, String contentLanguage) {
}
