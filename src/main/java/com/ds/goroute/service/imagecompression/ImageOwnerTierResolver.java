package com.ds.goroute.service.imagecompression;

import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionTier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * FREE or PRO for an image. FREE only when the owner is known and on the free plan (no
 * subscription row, or an expired one); a failed lookup or an unknown owner gives PRO, so a
 * paying user never loses quality to a guess.
 */
@Slf4j
@Component
public class ImageOwnerTierResolver {

    private final AiTripRepository aiTripRepository;

    /** A bulk run meets the same users again and again; a plan change shows within ten minutes. */
    private final Cache<UUID, ImageCompressionTier> tiers = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(20_000)
            .build();

    public ImageOwnerTierResolver(AiTripRepository aiTripRepository) {
        this.aiTripRepository = aiTripRepository;
    }

    public ImageCompressionTier forUser(UUID userId) {
        if (userId == null) {
            return ImageCompressionTier.PRO;
        }
        return tiers.get(userId, this::lookUp);
    }

    /** The tier of a stored image: the user in the key for groups that carry one, otherwise PRO. */
    public ImageCompressionTier forKey(ImageCompressionGroup group, String key) {
        if (group == null || !group.ownerInKey() || key == null || !key.startsWith(group.prefix())) {
            return ImageCompressionTier.PRO;
        }
        String rest = key.substring(group.prefix().length());
        int slash = rest.indexOf('/');
        String segment = slash < 0 ? rest : rest.substring(0, slash);
        try {
            return forUser(UUID.fromString(segment));
        } catch (IllegalArgumentException notAUserId) {
            return ImageCompressionTier.PRO;
        }
    }

    private ImageCompressionTier lookUp(UUID userId) {
        try {
            String tier = aiTripRepository.getSubscriptionTier(userId);
            return tier == null || "FREE".equalsIgnoreCase(tier.trim())
                    ? ImageCompressionTier.FREE
                    : ImageCompressionTier.PRO;
        } catch (Exception e) {
            log.warn("Image compression: plan lookup failed for user {}, using PRO: {}", userId, e.getMessage());
            return ImageCompressionTier.PRO;
        }
    }
}
