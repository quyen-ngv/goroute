package com.ds.goroute.thirdparty.scrape;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrapeJobTriggerResponse {
    private String jobId;
    private String status;
    private String pollUrl;
}
