package com.ds.goroute.dto.request;

import com.ds.goroute.type.AssetCollection;
import com.ds.goroute.type.AssetSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * A new library asset. The image is optional: an asset without one is a placeholder ("icon
 * still to find") that records can already reference, and its image is uploaded later.
 */
@Data
public class CreateLibraryAssetRequest {
    @NotNull
    private AssetCollection collection;

    /** Stable code inside the collection, lowercase kebab-case. */
    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = LibraryAssetKeys.PATTERN, message = "key must be lowercase kebab-case, e.g. fish-sauce")
    private String key;

    @NotBlank
    @Size(max = 200)
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

    /** An already-hosted image (for example one moved from the media library); usually left empty. */
    @Size(max = 2000)
    @Pattern(regexp = "^https://.+", message = "imageUrl must be an https URL")
    private String imageUrl;

    private AssetSource source;
    @Size(max = 2000)
    private String sourceUrl;
    @Size(max = 2000)
    private String licenseNote;
}
