package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One record that references a library asset. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryAssetUsageResponse {
    /** What kind of record, for example {@code FOOD}. */
    private String type;
    private UUID id;
    private String name;
}
