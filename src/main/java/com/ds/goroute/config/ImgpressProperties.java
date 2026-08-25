package com.ds.goroute.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "imgpress.service")
public class ImgpressProperties {

    @NotBlank
    private String url = "http://imgpress:3000";

    @Min(1)
    @Max(100)
    private int quality = 40;

    @Min(100)
    @Max(10_000)
    private int width = 1600;
}
