package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestArObject;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;
import com.ds.goroute.type.QuestArAnchorMode;
import com.ds.goroute.type.QuestArBehavior;

import java.math.BigDecimal;
import java.util.List;

/**
 * Turns a client's AR object (§3.15) into the stored shape: defaults filled in, ranges enforced,
 * numbers normalised so that the same object always writes the same JSON. That last part matters:
 * a client that cannot edit AR objects (the console) sends them back unchanged, and the builder
 * recognises "unchanged" by comparing the normalised JSON.
 *
 * <p>Only shape is checked here; whether the object is complete enough to publish is
 * {@link QuestDraftValidator}'s job, so a half-made draft can still be saved.
 */
final class QuestArObjects {

    static final int DEFAULT_SPAWN_RADIUS_M = 150;
    static final int MIN_SPAWN_RADIUS_M = 50;
    static final int MAX_SPAWN_RADIUS_M = 1000;
    static final int DEFAULT_WANDER_RADIUS_M = 30;
    static final int MIN_WANDER_RADIUS_M = 10;
    static final int MAX_WANDER_RADIUS_M = 200;
    static final BigDecimal MIN_SCALE = new BigDecimal("0.25");
    static final BigDecimal MAX_SCALE = new BigDecimal("4");
    static final int MAX_MARKERS = 3;
    static final BigDecimal MIN_MARKER_WIDTH_M = new BigDecimal("0.05");
    static final BigDecimal MAX_MARKER_WIDTH_M = new BigDecimal("10");
    /** How far from its landmark an IMAGE object may stand; further, a small angle error shows. */
    static final BigDecimal MAX_MARKER_OFFSET_M = new BigDecimal("20");
    static final int MAX_IMAGES = QuestBuilderServiceImpl.MAX_CHECKPOINT_IMAGES;

    private QuestArObjects() {
    }

    static QuestArObject fromInput(SaveQuestDraftRequest.ArObjectInput in, String where) {
        QuestArBehavior behavior = parse(QuestArBehavior.class, in.getBehavior(), QuestArBehavior.FIXED,
                where + " has an unknown AR object behaviour");
        QuestArAnchorMode anchor = parse(QuestArAnchorMode.class, in.getAnchorMode(), QuestArAnchorMode.APPROX,
                where + " has an unknown AR anchor mode");
        int spawn = in.getSpawnRadiusM() == null ? DEFAULT_SPAWN_RADIUS_M : in.getSpawnRadiusM();
        if (spawn < MIN_SPAWN_RADIUS_M || spawn > MAX_SPAWN_RADIUS_M) {
            throw invalid(where + " needs an AR object visibility radius of "
                    + MIN_SPAWN_RADIUS_M + "–" + MAX_SPAWN_RADIUS_M + " m");
        }
        Integer wander = null;
        if (behavior == QuestArBehavior.WANDER) {
            wander = in.getWanderRadiusM() == null ? DEFAULT_WANDER_RADIUS_M : in.getWanderRadiusM();
            if (wander < MIN_WANDER_RADIUS_M || wander > MAX_WANDER_RADIUS_M) {
                throw invalid(where + " needs an AR object zone of "
                        + MIN_WANDER_RADIUS_M + "–" + MAX_WANDER_RADIUS_M + " m");
            }
        }
        BigDecimal scale = in.getScale() == null ? BigDecimal.ONE : in.getScale();
        if (scale.compareTo(MIN_SCALE) < 0 || scale.compareTo(MAX_SCALE) > 0) {
            throw invalid(where + " has an AR object size outside " + MIN_SCALE + "–" + MAX_SCALE);
        }
        Integer heading = in.getHeadingDeg() == null ? null : Math.floorMod(in.getHeadingDeg(), 360);

        List<SaveQuestDraftRequest.MarkerInput> rawMarkers = in.getMarkers() == null ? List.of() : in.getMarkers();
        // An APPROX object keeps no landmarks, whatever the client sent.
        List<QuestArObject.Marker> markers = anchor == QuestArAnchorMode.IMAGE
                ? rawMarkers.stream().map(m -> marker(m, where)).toList()
                : List.of();
        if (markers.size() > MAX_MARKERS) {
            throw invalid(where + " has more than " + MAX_MARKERS + " AR landmarks");
        }

        List<String> images = in.getImageUrls() == null ? List.of() : in.getImageUrls().stream()
                .filter(url -> url != null && !url.isBlank()).map(String::trim).toList();
        if (images.size() > MAX_IMAGES) {
            throw invalid(where + " has more than " + MAX_IMAGES + " AR object photos");
        }
        images.forEach(url -> httpUrl(url, where));
        String audio = blankToNull(in.getAudioUrl());
        if (audio != null) {
            httpUrl(audio, where);
        }

        return normalize(new QuestArObject(in.getAssetId(), behavior.name(), anchor.name(),
                in.getLatitude(), in.getLongitude(), heading, spawn, wander, scale, markers,
                blankToNull(in.getTitle()), blankToNull(in.getDescription()), images, audio,
                audio == null ? null : in.getAudioSeconds()));
    }

    /** Numbers stripped of trailing zeros, so a round trip through JSONB compares equal. */
    static QuestArObject normalize(QuestArObject o) {
        return new QuestArObject(o.assetId(), o.behavior(), o.anchorMode(), strip(o.latitude()),
                strip(o.longitude()), o.headingDeg(), o.spawnRadiusM(), o.wanderRadiusM(), strip(o.scale()),
                o.markersOrEmpty().stream().map(m -> new QuestArObject.Marker(m.imageUrl(), strip(m.widthM()),
                        m.offset() == null ? null : new QuestArObject.Vec3(strip(m.offset().x()),
                                strip(m.offset().y()), strip(m.offset().z())),
                        strip(m.yawDeg()))).toList(),
                o.title(), o.description(), o.imageUrlsOrEmpty(), o.audioUrl(), o.audioSeconds());
    }

    private static QuestArObject.Marker marker(SaveQuestDraftRequest.MarkerInput m, String where) {
        String at = where + " AR landmark";
        String url = httpUrl(m.getImageUrl(), at);
        BigDecimal width = m.getWidthM();
        if (width == null || width.compareTo(MIN_MARKER_WIDTH_M) < 0 || width.compareTo(MAX_MARKER_WIDTH_M) > 0) {
            throw invalid(at + " needs a width of " + MIN_MARKER_WIDTH_M + "–" + MAX_MARKER_WIDTH_M + " m");
        }
        SaveQuestDraftRequest.Vec3Input o = m.getOffset();
        if (o == null || o.getX() == null || o.getY() == null || o.getZ() == null) {
            throw invalid(at + " has no position for the object");
        }
        for (BigDecimal axis : List.of(o.getX(), o.getY(), o.getZ())) {
            if (axis.abs().compareTo(MAX_MARKER_OFFSET_M) > 0) {
                throw invalid(at + " is more than " + MAX_MARKER_OFFSET_M + " m from the object");
            }
        }
        BigDecimal yaw = m.getYawDeg() == null ? BigDecimal.ZERO : m.getYawDeg();
        if (yaw.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw invalid(at + " has a turn outside -180–180°");
        }
        return new QuestArObject.Marker(url, width, new QuestArObject.Vec3(o.getX(), o.getY(), o.getZ()), yaw);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback, String message) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw invalid(message);
        }
    }

    private static String httpUrl(String raw, String where) {
        String url = raw == null ? "" : raw.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw invalid(where + " has a link that is not a URL");
        }
        return url;
    }

    private static BigDecimal strip(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
