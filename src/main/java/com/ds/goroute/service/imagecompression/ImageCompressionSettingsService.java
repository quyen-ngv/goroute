package com.ds.goroute.service.imagecompression;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ImageCompressionTier;
import org.springframework.stereotype.Service;

/** The {@code IMAGE_COMPRESSION.*} config, read through the cached business config. */
@Service
public class ImageCompressionSettingsService {

    private final BusinessConfigService businessConfigService;

    public ImageCompressionSettingsService(BusinessConfigService businessConfigService) {
        this.businessConfigService = businessConfigService;
    }

    public record TierSettings(int quality, int maxLongEdge) {
    }

    public record Settings(boolean autoEnabled, TierSettings free, TierSettings pro, int minSavingsPercent) {
        public TierSettings forTier(ImageCompressionTier tier) {
            return tier == ImageCompressionTier.FREE ? free : pro;
        }
    }

    public Settings current() {
        return new Settings(
                businessConfigService.getBoolean(BusinessConfigKey.IMAGE_COMPRESSION_AUTO_ENABLED),
                new TierSettings(
                        businessConfigService.getInt(BusinessConfigKey.IMAGE_COMPRESSION_QUALITY_FREE),
                        businessConfigService.getInt(BusinessConfigKey.IMAGE_COMPRESSION_MAX_LONG_EDGE_FREE)),
                new TierSettings(
                        businessConfigService.getInt(BusinessConfigKey.IMAGE_COMPRESSION_QUALITY_PRO),
                        businessConfigService.getInt(BusinessConfigKey.IMAGE_COMPRESSION_MAX_LONG_EDGE_PRO)),
                businessConfigService.getInt(BusinessConfigKey.IMAGE_COMPRESSION_MIN_SAVINGS_PERCENT));
    }
}
