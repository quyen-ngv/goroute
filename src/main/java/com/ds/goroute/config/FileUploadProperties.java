package com.ds.goroute.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.upload")
public class FileUploadProperties {

    @NotNull
    private DataSize maxImageSize = DataSize.ofMegabytes(10);

    @NotNull
    private DataSize maxProImageSize = DataSize.ofMegabytes(50);

    @NotNull
    private DataSize maxVideoSize = DataSize.ofMegabytes(50);

    @Min(1)
    @Max(50)
    private int maxBatchFiles = 10;

    @NotEmpty
    private List<String> allowedImageTypes = List.of("image/jpeg", "image/png", "image/webp");

    @NotEmpty
    private List<String> allowedVideoTypes = List.of("video/mp4", "video/quicktime", "video/webm");

    @AssertTrue(message = "maxImageSize must be between 1 byte and 50 MB")
    public boolean isMaxImageSizeValid() {
        return maxImageSize != null
                && maxImageSize.toBytes() > 0
                && maxImageSize.toBytes() <= DataSize.ofMegabytes(50).toBytes();
    }

    @AssertTrue(message = "maxProImageSize must be between 1 byte and 100 MB")
    public boolean isMaxProImageSizeValid() {
        return maxProImageSize != null
                && maxProImageSize.toBytes() > 0
                && maxProImageSize.toBytes() <= DataSize.ofMegabytes(100).toBytes();
    }

    @AssertTrue(message = "maxVideoSize must be between 1 byte and 50 MB")
    public boolean isMaxVideoSizeValid() {
        return maxVideoSize != null
                && maxVideoSize.toBytes() > 0
                && maxVideoSize.toBytes() <= DataSize.ofMegabytes(50).toBytes();
    }
}
