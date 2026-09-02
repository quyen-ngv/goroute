package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertMessageTemplateRequest {
    @NotBlank @Size(max = 120)
    private String title;
    @NotBlank @Size(max = 4000)
    private String body;
    @Min(0) @Max(10000)
    private Integer sortOrder;
}
