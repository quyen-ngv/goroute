package com.ds.goroute.service.marketplace;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accepts only attachments this server stored for this conversation.
 *
 * <p>An attachment is stored and broadcast as the client sent it, and every other member's app
 * then loads its URL. Without this a message could carry a link to any host (a tracking pixel
 * that reports who opened the thread, or somebody else's thread's photo) and arbitrary keys
 * that the apps and admin tools would render. So: a closed set of keys, and URLs that are ours —
 * the scheme and host our storage serves from (HTTPS in every deployed environment), under this
 * conversation's upload prefix
 * ({@code chat/<conversationId>/}, where {@code POST .../attachments} writes).
 */
@Component
@RequiredArgsConstructor
public class ChatAttachmentValidator {

    /** Same ceiling as the upload endpoint. */
    static final int MAX_ATTACHMENTS = 10;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_TEXT_LENGTH = 255;
    private static final long MAX_NUMBER = 1_000_000_000_000L;

    private static final Set<String> URL_KEYS = Set.of("url", "thumbnailUrl");
    private static final Set<String> TEXT_KEYS = Set.of("type", "name", "mimeType");
    /** {@code w}/{@code h} are the short forms the app's bubble already reads. */
    private static final Set<String> NUMBER_KEYS = Set.of("size", "width", "height", "w", "h");

    private final StorageService storageService;

    /** @return the attachments to store, as validated copies; empty when there are none */
    public List<Map<String, Object>> validate(UUID conversationId, List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw bad("At most " + MAX_ATTACHMENTS + " attachments can be sent at once");
        }
        URI storage = storageOrigin();
        String keyPrefix = "chat/" + conversationId + "/";
        return attachments.stream()
                .map(attachment -> validateOne(attachment, storage, keyPrefix))
                .toList();
    }

    private Map<String, Object> validateOne(Map<String, Object> attachment, URI storage, String keyPrefix) {
        if (attachment == null || attachment.get("url") == null) throw bad("Each attachment needs a url");
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> field : attachment.entrySet()) {
            String key = field.getKey();
            Object value = field.getValue();
            if (value == null) continue;
            if (URL_KEYS.contains(key)) {
                clean.put(key, ownUrl(value, storage, keyPrefix));
            } else if (TEXT_KEYS.contains(key)) {
                String text = String.valueOf(value);
                if (text.length() > MAX_TEXT_LENGTH) throw bad("Attachment " + key + " is too long");
                clean.put(key, text);
            } else if (NUMBER_KEYS.contains(key)) {
                if (!(value instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > MAX_NUMBER) {
                    throw bad("Attachment " + key + " must be a non-negative number");
                }
                clean.put(key, number);
            } else {
                throw bad("Unsupported attachment field: " + key);
            }
        }
        return clean;
    }

    private String ownUrl(Object value, URI storage, String keyPrefix) {
        String url = String.valueOf(value).trim();
        if (url.length() > MAX_URL_LENGTH) throw bad("Attachment url is too long");
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException malformed) {
            throw bad("Attachment url is not valid");
        }
        boolean isOurs = storage != null
                && uri.getScheme() != null
                && uri.getScheme().equalsIgnoreCase(storage.getScheme())
                && uri.getHost() != null
                && uri.getHost().equalsIgnoreCase(storage.getHost())
                && uri.getPort() == storage.getPort()
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null;
        String objectKey = isOurs ? storageService.extractObjectKey(url) : null;
        if (objectKey == null || !objectKey.startsWith(keyPrefix) || objectKey.contains("..")) {
            throw bad("Attachments must be uploaded to this conversation first");
        }
        return url;
    }

    /** Where our storage hands out URLs today, asked of the storage itself; null refuses everything. */
    private URI storageOrigin() {
        try {
            String sample = storageService.urlFor("chat/probe");
            URI origin = sample == null ? null : URI.create(sample);
            return origin == null || origin.getHost() == null || origin.getScheme() == null ? null : origin;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private BusinessException bad(String message) {
        return new BusinessException(ErrorConstant.BAD_REQUEST, message);
    }
}
