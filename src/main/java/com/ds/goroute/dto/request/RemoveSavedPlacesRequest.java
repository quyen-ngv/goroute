package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Places to take off the saved list: out of one category when {@code category} is set, else
 * off the list altogether.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveSavedPlacesRequest {

    /** The list reads at most 500 saves, so a whole album fits in one request. */
    @NotEmpty
    @Size(max = 500)
    private List<@NotBlank @Size(max = 255) String> placeIds;

    /** saved_places.category is VARCHAR(50). */
    @Size(max = 50)
    private String category;
}
