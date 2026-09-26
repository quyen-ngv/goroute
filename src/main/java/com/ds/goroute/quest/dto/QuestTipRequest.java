package com.ds.goroute.quest.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import lombok.Data;

import java.util.UUID;

/** A tip to a creator. The message is public when {@code messagePublic}, so it goes through the filter. */
@Data
public class QuestTipRequest {

    private Integer amount;
    private UUID runId;

    @ModeratedText(contentType = ModeratedContentType.QUEST, label = "tip.message")
    private String message;

    private boolean messagePublic;
}
