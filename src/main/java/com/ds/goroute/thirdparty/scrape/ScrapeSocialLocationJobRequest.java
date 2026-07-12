package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeSocialLocationJobRequest {
    private String url;
    private String language;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("goroute_job_id")
    private UUID gorouteJobId;

    @JsonProperty("max_audio_seconds")
    private Integer maxAudioSeconds;

    @JsonProperty("max_frames")
    private Integer maxFrames;

    @JsonProperty("frame_interval_seconds")
    private Integer frameIntervalSeconds;

    @JsonProperty("image_max_width")
    private Integer imageMaxWidth;

    @JsonProperty("image_jpeg_quality")
    private Integer imageJpegQuality;

    @JsonProperty("max_candidates")
    private Integer maxCandidates;

    @JsonProperty("include_map_search")
    private Boolean includeMapSearch;

    @JsonProperty("map_search_limit")
    private Integer mapSearchLimit;

    private Boolean headless;
}
