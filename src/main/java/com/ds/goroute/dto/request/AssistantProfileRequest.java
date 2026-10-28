package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole list of what Pengoo keeps about the caller, replacing the old one; an empty list
 * forgets it. Each item must be 1..80 characters once trimmed (checked by the service).
 */
@Data
public class AssistantProfileRequest {
    @NotNull
    @Size(max = 12)
    private List<@NotNull @Size(max = 200) String> items = new ArrayList<>();
}
