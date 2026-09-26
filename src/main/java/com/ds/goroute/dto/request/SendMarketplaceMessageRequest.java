package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.ds.goroute.type.MarketplaceMessageType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Data
public class SendMarketplaceMessageRequest {
 @NotBlank @Size(max=100) private String clientMessageId;
 private MarketplaceMessageType messageType=MarketplaceMessageType.TEXT;
 // CHAT visibility: the term list refuses a message to the person typing it, and that is
 // the whole of it. Nothing is parked for a human to read, because nobody is allowed to
 // read these threads.
 @Size(max=10000)
 @ModeratedText(contentType = ModeratedContentType.CHAT_MESSAGE, visibility = ModerationVisibility.CHAT)
 private String content;
 private List<Map<String,Object>> attachments;

    /**
     * The message this one answers.
     *
     * <p>Validated against the same conversation on the way in: a reply is a quote, and a
     * quote of something from another thread would leak it.
     */
    private UUID replyToMessageId;

    /**
     * People named with {@code @} in {@link #content}, as the client parsed them.
     *
     * <p>Sent by the client rather than parsed on the server because the client already
     * knows which name it turned into which member when the author picked from the list;
     * re-deriving it from the text would guess. The server still checks every id is in the
     * conversation, so a crafted request cannot notify a stranger.
     */
    private List<UUID> mentionedUserIds;
}
