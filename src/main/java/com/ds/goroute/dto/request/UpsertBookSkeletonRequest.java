package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.BookSlotDefResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertBookSkeletonRequest {
    @NotBlank
    private String skeletonKey;

    private Integer version;
    private String name;

    @NotBlank
    private String pageType;

    private Integer photoCount;

    @NotNull
    private Integer canvasWidth;

    @NotNull
    private Integer canvasHeight;

    @NotEmpty
    private List<BookSlotDefResponse> slotDefs;

    private Boolean isActive;
}
