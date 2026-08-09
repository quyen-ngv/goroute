package com.ds.goroute.dto.request;import com.ds.goroute.type.MarketplaceReviewResponseStatus;import jakarta.validation.constraints.*;import lombok.Data;
@Data public class UpsertMarketplaceReviewResponseRequest {@NotBlank @Size(max=5000) private String responseText;private MarketplaceReviewResponseStatus status=MarketplaceReviewResponseStatus.PUBLISHED;private Long expectedVersion;}
