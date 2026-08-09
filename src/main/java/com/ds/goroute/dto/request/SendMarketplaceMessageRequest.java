package com.ds.goroute.dto.request;

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
 @Size(max=10000) private String content;
 private List<Map<String,Object>> attachments;
}
