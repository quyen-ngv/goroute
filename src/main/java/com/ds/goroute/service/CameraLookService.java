package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateCameraLookRequest;
import com.ds.goroute.dto.request.ReorderCameraLooksRequest;
import com.ds.goroute.dto.request.UpdateCameraLookRequest;
import com.ds.goroute.dto.response.CameraLookManifestResponse;
import com.ds.goroute.dto.response.CameraLookResponse;
import com.ds.goroute.dto.response.CameraLookUploadResponse;
import com.ds.goroute.type.CameraLookKind;
import com.ds.goroute.type.CameraLookStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Photo filters and frames of the check-in camera (epic 08, V293). */
public interface CameraLookService {

    /** Every look matching the filters, in display order, each with its usage count. */
    List<CameraLookResponse> adminList(CameraLookKind kind, CameraLookStatus status, String q);

    CameraLookResponse adminGet(UUID lookId);

    CameraLookResponse adminCreate(CreateCameraLookRequest request, UUID actorUserId);

    CameraLookResponse adminUpdate(UUID lookId, UpdateCameraLookRequest request, UUID actorUserId);

    /** Sets each look's sort order to its position in the list; returns the kind in its new order. */
    List<CameraLookResponse> adminReorder(ReorderCameraLooksRequest request, UUID actorUserId);

    /** Refused while a check-in photo still carries the key. */
    void adminDelete(UUID lookId);

    /** Stores a LUT, frame or thumbnail PNG byte for byte under the never-compressed prefix. */
    CameraLookUploadResponse adminUpload(MultipartFile file, UUID actorUserId);

    /** ACTIVE looks inside their date window, in the app's manifest shape. */
    CameraLookManifestResponse publicManifest();
}
