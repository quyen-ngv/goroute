package com.ds.goroute.service.track;

/**
 * One recorded GPS fix.
 *
 * @param timeMs     epoch millis
 * @param latitude   degrees
 * @param longitude  degrees
 * @param accuracyM  reported horizontal accuracy in metres, null when the OS gave none
 * @param altitudeM  metres, null when unknown
 * @param speedDms   decimetres per second as reported by the OS, null when unknown
 */
public record TrackPoint(
        long timeMs,
        double latitude,
        double longitude,
        Integer accuracyM,
        Integer altitudeM,
        Integer speedDms) {
}
