package com.ds.goroute.dto.response;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.ImageUploadOutcome;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Backward-compatible batch image upload response.
 *
 * <p>{@code data} remains the accepted URL list consumed by released clients.
 * {@code imageObjects} supplies one result per requested file for newer clients
 * that need filename, moderation, or failure metadata.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageUploadBatchResponse extends BaseResponse<List<String>> {

    @JsonProperty("imageObjects")
    private List<UploadedImageResponse> imageObjects;

    public static ImageUploadBatchResponse fromOutcomes(List<ImageUploadOutcome> outcomes) {
        List<ImageUploadOutcome> uploadOutcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        ImageUploadBatchResponse response = new ImageUploadBatchResponse();
        response.setData(uploadOutcomes.stream()
                .filter(ImageUploadOutcome::isAccepted)
                .map(ImageUploadOutcome::url)
                .toList());
        response.setImageObjects(uploadOutcomes.stream()
                .map(UploadedImageResponse::from)
                .toList());
        response.getMeta().setCode(ErrorConstant.SUCCESS);
        response.getMeta().setMessage("OK");
        return response;
    }
}
