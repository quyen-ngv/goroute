package com.ds.goroute.dto.request;

import com.ds.goroute.type.ImageCompressionGroup;
import lombok.Data;

import java.util.List;

/** Starts the bulk image compression run; empty or null {@code groups} means every group. */
@Data
public class StartImageCompressionRunRequest {
    private List<ImageCompressionGroup> groups;
}
