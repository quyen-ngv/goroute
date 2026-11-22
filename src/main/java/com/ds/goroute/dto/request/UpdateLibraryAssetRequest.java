package com.ds.goroute.dto.request;

import com.ds.goroute.type.AssetSource;
import com.ds.goroute.type.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Partial update: a {@code null} field keeps its value, an empty string clears an optional text.
 * The collection cannot change, because records that reference the asset expect its kind.
 * The image is replaced through the upload endpoint, or set here to an already-hosted URL.
 */
@Data
public class UpdateLibraryAssetRequest {
    @Size(max = 80)
    @Pattern(regexp = LibraryAssetKeys.PATTERN, message = "key must be lowercase kebab-case, e.g. fish-sauce")
    private String key;

    @Size(min = 1, max = 200)
    private String nameEn;
    @Size(max = 200)
    private String nameVi;
    @Size(max = 200)
    private String nameJa;
    @Size(max = 200)
    private String nameKo;

    @Size(max = 2000)
    private String description;

    @Size(max = 20)
    private List<@NotBlank @Size(max = 40) String> tags;

    /** Empty string removes the image (the asset becomes a placeholder again). */
    @Size(max = 2000)
    @Pattern(regexp = "^$|^https://.+", message = "imageUrl must be an https URL")
    private String imageUrl;

    private AssetSource source;
    @Size(max = 2000)
    private String sourceUrl;
    @Size(max = 2000)
    private String licenseNote;

    private AssetStatus status;
}
