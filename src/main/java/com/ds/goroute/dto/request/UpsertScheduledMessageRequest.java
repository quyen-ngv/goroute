package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.MarketplaceScheduledMessageAudience;
import com.ds.goroute.type.MarketplaceScheduledMessageStatus;
import com.ds.goroute.type.MarketplaceScheduledMessageTrigger;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * A partner's automation rule. The body goes through the same declarative moderation as a typed
 * chat message ({@code CHAT_MESSAGE} / {@code DIRECT}), checked once here at authoring time rather
 * than on every delivery.
 */
@Data
public class UpsertScheduledMessageRequest {
    /** Optional: the quick reply this body was copied from. The body is still stored inline. */
    private UUID templateId;

    @NotNull
    private MarketplaceScheduledMessageTrigger trigger;

    /** Hours before or after the trigger moment; the direction belongs to the trigger. */
    @NotNull @Min(0) @Max(8760)
    private Integer offsetHours;

    @NotBlank @Size(max = 2000)
    @ModeratedText(contentType = ModeratedContentType.CHAT_MESSAGE, visibility = ModerationVisibility.DIRECT)
    private String body;

    @NotNull
    private MarketplaceScheduledMessageAudience appliesTo;

    /** Omitted means {@code ENABLED}; the console uses it to pause a rule without deleting it. */
    private MarketplaceScheduledMessageStatus status;
}
