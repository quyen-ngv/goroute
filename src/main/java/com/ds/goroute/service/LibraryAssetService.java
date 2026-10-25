package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateLibraryAssetRequest;
import com.ds.goroute.dto.request.UpdateLibraryAssetRequest;
import com.ds.goroute.dto.response.LibraryAssetResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.type.AssetCollection;
import com.ds.goroute.type.AssetStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The shared image library (epic 06 §2.5b). Records reference an asset by id and read its
 * current image when they are served, so replacing the image updates every record at once.
 */
public interface LibraryAssetService {

    PageResponse<LibraryAssetResponse> adminSearch(AssetCollection collection, AssetStatus status, String q,
                                                   boolean missingImage, int page, int size);

    LibraryAssetResponse adminGet(UUID assetId);

    LibraryAssetResponse adminCreate(CreateLibraryAssetRequest request, UUID actorUserId);

    LibraryAssetResponse adminUpdate(UUID assetId, UpdateLibraryAssetRequest request, UUID actorUserId);

    /** Stores the file through the upload door (type, size, moderation) and makes it the asset's image. */
    LibraryAssetResponse adminReplaceImage(UUID assetId, MultipartFile file, UUID actorUserId);

    /** Refused with 409 while any record still references the asset; archive it instead. */
    void adminDelete(UUID assetId);
}
