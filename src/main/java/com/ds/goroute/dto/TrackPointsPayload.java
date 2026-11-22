package com.ds.goroute.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GPS points in the columnar wire format of the travel tracker
 * (.docs/designs/TRAVEL_TRACKER_API_CONTRACT.md). Used both for uploads and for returning
 * raw points to their owner, and stored as-is in {@code trip_track_chunks.points}.
 *
 * <ul>
 *   <li>{@code t0}: epoch millis of the first point; {@code dt}: cumulative millis, dt[0] = 0.</li>
 *   <li>{@code lat}/{@code lng}: degrees x 1e7 as integers.</li>
 *   <li>{@code acc}/{@code alt}: metres, {@code spd}: decimetres per second; any may be null.</li>
 * </ul>
 * Structural checks (equal lengths, increasing time) live in {@code TrackPointsCodec}, so a
 * bad chunk gets one precise error rather than a cascade of bean-validation messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackPointsPayload {

    public static final int FORMAT_VERSION = 1;

    private Integer v;
    private Long t0;
    private List<Long> dt;
    private List<Long> lat;
    private List<Long> lng;
    private List<Integer> acc;
    private List<Integer> alt;
    private List<Integer> spd;
}
