package com.ds.goroute.dto.response;

import com.ds.goroute.dto.TrackPointsPayload;
import com.ds.goroute.type.TrackAccuracyMode;
import com.ds.goroute.type.TrackSegmentKind;
import com.ds.goroute.type.TrackVisibility;
import com.ds.goroute.type.TrackVisitKind;
import com.ds.goroute.type.TrackVisitStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response shapes of the travel tracker API, grouped in one file because they only make
 * sense together (.docs/designs/TRAVEL_TRACKER_API_CONTRACT.md). Timestamps are UTC without
 * an offset, like every other response of this API.
 */
public final class TrackResponses {

    private TrackResponses() {
    }

    @Builder
    public record Context(
            boolean enabled,
            boolean canRecord,
            Window window,
            Config config,
            Settings mySettings,
            List<Member> members) {
    }

    public record Window(LocalDate startDate, LocalDate endDate, String timezone, int padDays) {
    }

    @Builder
    public record Config(
            Map<TrackAccuracyMode, Integer> maxAccuracyM,
            Map<TrackAccuracyMode, Integer> distanceFilterM,
            int maxSpeedKmh,
            int stationaryRadiusM,
            int stationaryMinutes,
            int arrivalRadiusM,
            int arrivalMinutes,
            int nudgeCooldownMinutes,
            int nudgeDailyMax,
            int quietStartHour,
            int quietEndHour,
            int lowBatteryPct,
            int stopBatteryPct,
            int chunkMaxPoints,
            int checkinMaxAccuracyM,
            int offPlanRadiusM,
            int offPlanMinMinutes,
            int offPlanNameRadiusM) {
    }

    public record Settings(
            TrackVisibility visibility,
            boolean trimDayEnds,
            TrackAccuracyMode defaultAccuracy,
            boolean autoEnabled,
            long dataVersion) {
    }

    public record Member(
            UUID userId,
            String displayName,
            String avatarUrl,
            boolean isMe,
            boolean hasTrack,
            boolean visibleToMe) {
    }

    public record ChunkUpload(int acceptedPoints, int rejectedPoints, List<DaySummary> days) {
    }

    @Builder
    public record DaySummary(
            LocalDate date,
            int dayNumber,
            int distanceM,
            int flightDistanceM,
            int movingSeconds,
            int elevationGainM,
            int elevationLossM,
            int pointCount,
            LocalDateTime firstAt,
            LocalDateTime lastAt) {
    }

    public record Segment(TrackSegmentKind kind, String polyline) {
    }

    public record Visit(
            UUID id,
            TrackVisitKind kind,
            UUID activityId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime arrivedAt,
            LocalDateTime departedAt,
            TrackVisitStatus status,
            UUID placeId,
            UUID createdActivityId) {
    }

    public record Day(
            UUID userId,
            LocalDate date,
            int dayNumber,
            DaySummary summary,
            List<Segment> segments,
            double[] bbox,
            List<Visit> visits,
            boolean outsideTripWindow,
            List<String> provinceCodes) {
    }

    public record DayPoints(LocalDate date, TrackPointsPayload points) {
    }

    public record Deletion(List<DaySummary> days, List<LocalDate> deletedDates) {
    }

    /** Result of adding an off-plan stop to the itinerary. */
    public record AddedToItinerary(Visit visit, UUID activityId) {
    }

    /** One member's day as a person outside the trip sees it: the clipped line, no stops, no times. */
    public record PublicDay(
            UUID userId,
            String displayName,
            String avatarUrl,
            LocalDate date,
            int dayNumber,
            int distanceM,
            List<Segment> segments,
            double[] bbox) {
    }

    public record Province(String code, String name) {
    }

    @Builder
    public record Recap(
            UUID userId,
            long distanceM,
            long flightDistanceM,
            long movingSeconds,
            long elevationGainM,
            int dayCount,
            List<Province> provincesPassed,
            int visitedActivityCount,
            int plannedActivityCount,
            int checkinCount) {
    }

    public record MyStats(long totalDistanceM, int tripCount, List<Province> provincesPassed) {
    }

    /** The owner's whole route of a trip, for the Travel Book route page. */
    public record BookRoute(List<Segment> segments, double[] bbox, long distanceM) {
    }

    /** One trip on the caller's own overview map ("Hành trình của tôi"): the whole route, thinned. */
    @Builder
    public record MyTripRoute(
            UUID tripId,
            String tripName,
            String coverImageUrl,
            LocalDate startDate,
            LocalDate endDate,
            long distanceM,
            int dayCount,
            int provinceCount,
            List<Segment> segments,
            double[] bbox) {
    }

    /**
     * The caller's own route of a trip or of one day, cut the way outsiders would see it
     * (privacy zones out, day ends trimmed when that option is on), for a picture they share.
     */
    public record ShareRoute(
            List<Segment> segments,
            double[] bbox,
            long distanceM,
            int dayCount,
            List<Province> provincesPassed) {
    }

    public record Zone(
            UUID id,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusM,
            long dataVersion) {
    }
}
