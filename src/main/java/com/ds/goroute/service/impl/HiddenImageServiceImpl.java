package com.ds.goroute.service.impl;

import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.ImageModerationResult;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.service.HiddenImageService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.ErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HiddenImageServiceImpl implements HiddenImageService {

    static final String PLACEHOLDER_KEY = "system/moderation/image-hidden.png";
    /** A UUID file name plus its extension; anything shorter cannot be an uploaded image. */
    private static final int MIN_FILE_NAME_LENGTH = 36;
    private static final int PLACEHOLDER_WIDTH = 400;
    private static final int PLACEHOLDER_HEIGHT = 300;
    private static final Color PLACEHOLDER_COLOR = new Color(0xE5, 0xE7, 0xEB);

    private final ModerationAuditRepository auditRepository;
    private final ModerationFlagRepository flagRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;

    /** File names of hidden images. Replaced wholesale, never mutated in place. */
    private volatile Set<String> hiddenFileNames = Set.of();
    private volatile boolean isPlaceholderStored;

    @Override
    public void hide(ImageModerationResult image, ModerationCategory category) {
        ensurePlaceholderStored();
        flagRepository.insertTakedown(ContentTakedown.builder()
                .id(UUID.randomUUID())
                .contentType(ModeratedContentType.IMAGE)
                .contentId(image.getId())
                .ownerId(image.getUserId())
                .category(category)
                .reason("Automatic image moderation: " + category.name())
                .removedAt(LocalDateTime.now())
                .build());
        add(image.getObjectKey());
        notifyOwner(image, category);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void reload() {
        try {
            Set<String> names = new HashSet<>();
            for (String key : auditRepository.findHiddenImageKeys()) {
                String name = fileName(key);
                if (name != null) {
                    names.add(name);
                }
            }
            hiddenFileNames = Set.copyOf(names);
            if (!names.isEmpty()) {
                ensurePlaceholderStored();
            }
        } catch (RuntimeException exception) {
            log.warn("Could not load hidden images: {}", exception.getMessage(), exception);
        }
    }

    @Override
    public boolean isHidden(String value) {
        Set<String> names = hiddenFileNames;
        if (names.isEmpty() || value == null || value.length() < MIN_FILE_NAME_LENGTH) {
            return false;
        }
        String name = fileName(value);
        return name != null && names.contains(name);
    }

    @Override
    public String placeholderUrl() {
        return storageService.urlFor(PLACEHOLDER_KEY);
    }

    private synchronized void add(String objectKey) {
        String name = fileName(objectKey);
        if (name == null) {
            return;
        }
        Set<String> names = new HashSet<>(hiddenFileNames);
        names.add(name);
        hiddenFileNames = Set.copyOf(names);
    }

    /** Last path segment without a query string or fragment. */
    static String fileName(String value) {
        if (value == null) {
            return null;
        }
        int end = value.length();
        int query = value.indexOf('?');
        if (query >= 0) {
            end = query;
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        int start = value.lastIndexOf('/', end - 1) + 1;
        return end - start < MIN_FILE_NAME_LENGTH ? null : value.substring(start, end);
    }

    private void notifyOwner(ImageModerationResult image, ModerationCategory category) {
        if (image.getUserId() == null) {
            return;
        }
        try {
            notificationService.createNotification(
                    image.getUserId(),
                    null,
                    NotificationType.ADMIN_MESSAGE,
                    ErrorMessages.of("moderation.image.hidden"),
                    ErrorMessages.of("moderation.category." + category.name()),
                    Map.of("contentType", ModeratedContentType.IMAGE.name(),
                            "contentId", image.getId().toString(),
                            "category", category.name()),
                    null);
        } catch (RuntimeException exception) {
            // The image stays hidden either way; a missing notice is a support ticket, not a leak.
            log.warn("Could not notify {} about a hidden image: {}",
                    image.getUserId(), exception.getMessage(), exception);
        }
    }

    /**
     * Writes the placeholder to the bucket the first time it is needed, so no deploy step
     * has to remember it. A failure only means the hidden image shows as a broken one.
     */
    private void ensurePlaceholderStored() {
        if (isPlaceholderStored) {
            return;
        }
        try {
            if (storageService.readObject(PLACEHOLDER_KEY) == null) {
                byte[] png = placeholderPng();
                storageService.uploadFile(PLACEHOLDER_KEY, new ByteArrayInputStream(png), "image/png", png.length);
            }
            isPlaceholderStored = true;
        } catch (RuntimeException | IOException exception) {
            log.warn("Could not store the hidden-image placeholder: {}", exception.getMessage(), exception);
        }
    }

    private static byte[] placeholderPng() throws IOException {
        BufferedImage image = new BufferedImage(PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(PLACEHOLDER_COLOR);
            graphics.fillRect(0, 0, PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
