package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSocialLocationJobRequest {
    @NotBlank(message = "URL is required")
    private String url;

    @Builder.Default
    private String language = "vi";

    @Min(10)
    @Max(600)
    private Integer maxAudioSeconds;

    @Min(1)
    @Max(100)
    @Builder.Default
    private Integer maxFrames = 50;

    @Min(1)
    @Max(30)
    private Integer frameIntervalSeconds;

    @Min(256)
    @Max(1280)
    @Builder.Default
    private Integer imageMaxWidth = 384;

    @Min(2)
    @Max(31)
    @Builder.Default
    private Integer imageJpegQuality = 10;

    @Min(1)
    @Max(50)
    private Integer maxCandidates;

    @Builder.Default
    private Boolean includeMapSearch = true;

    @Min(1)
    @Max(10)
    @Builder.Default
    private Integer mapSearchLimit = 1;

    @Builder.Default
    private Boolean headless = true;
}
