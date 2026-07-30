package com.ds.goroute.dto.request;import jakarta.validation.constraints.*;import lombok.Data;
@Data public class UpsertMarketplaceReviewResponseRequest {@NotBlank @Size(max=5000) private String responseText;@Pattern(regexp="PUBLISHED|HIDDEN")private String status="PUBLISHED";private Long expectedVersion;}
