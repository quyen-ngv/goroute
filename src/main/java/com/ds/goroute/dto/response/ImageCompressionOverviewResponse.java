package com.ds.goroute.dto.response;

import com.ds.goroute.type.ImageCompressionGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The console's image compression page: current settings and totals per storage group. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCompressionOverviewResponse {
    private boolean autoEnabled;
    private int freeQuality;
    private int freeMaxLongEdge;
    private int proQuality;
    private int proMaxLongEdge;
    private int minSavingsPercent;
    private List<Group> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private ImageCompressionGroup group;
        private String prefix;
        /** Whether FREE/PRO follows the user in the key; otherwise every image of the group is PRO. */
        private boolean ownerInKey;
        private long compressed;
        private long noSavings;
        private long notImages;
        /** Over COMPRESSED images only. */
        private long originalBytes;
        private long compressedBytes;
    }
}
