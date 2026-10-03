package com.ds.goroute.thirdparty.google.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** The subset of a Places API (New) {@code Place} that {@link GooglePlacesClient#PLACE_FIELDS} asks for. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GooglePlace {
    private String id;
    private LocalizedText displayName;
    private String formattedAddress;
    private LatLng location;
    private List<String> types;
    private String primaryType;
    private String googleMapsUri;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalizedText {
        private String text;
        private String languageCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LatLng {
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}
