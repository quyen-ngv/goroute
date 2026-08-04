package com.ds.goroute.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpsertPartnerPlaceRequest {
    @NotNull private UUID organizationId;
    @NotBlank @Size(max=500) private String title;
    @Pattern(regexp="FOOD_AND_DRINK|CULTURE_AND_HERITAGE|NATURE_AND_OUTDOORS|SHOPPING_AND_MARKET|ATTRACTIONS|ACCOMMODATION|OTHER")
    private String placeGroup="ACCOMMODATION";
    @Size(max=255) private String category;
    private String address;
    @DecimalMin("-90") @DecimalMax("90") private BigDecimal latitude;
    @DecimalMin("-180") @DecimalMax("180") private BigDecimal longitude;
    @Size(max=100) private String timezone;
    @Size(max=50) private String phone;
    private String website;
    private String thumbnail;
    private List<String> images;
    private List<String> destinations;
    private String description;
    private JsonNode attributes;
    private Long expectedVersion;
}
