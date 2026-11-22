package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where an uploaded LUT, frame or thumbnail PNG is served from. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CameraLookUploadResponse {
    private String url;
}
