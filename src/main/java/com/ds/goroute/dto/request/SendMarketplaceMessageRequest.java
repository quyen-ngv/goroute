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
import java.util.Map;

@Data
public class SendMarketplaceMessageRequest {
 @NotBlank @Size(max=100) private String clientMessageId;
 private MarketplaceMessageType messageType=MarketplaceMessageType.TEXT;
 // DIRECT visibility: by policy the product does not filter one-to-one messages
 // proactively, only on report. The marking is still here so the rule lives in
 // configuration rather than in the absence of an annotation.
 @Size(max=10000)
 @ModeratedText(contentType = ModeratedContentType.CHAT_MESSAGE, visibility = ModerationVisibility.DIRECT)
 private String content;
 private List<Map<String,Object>> attachments;
}
