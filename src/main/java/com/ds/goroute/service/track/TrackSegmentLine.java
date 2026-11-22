package com.ds.goroute.service.track;

import com.ds.goroute.type.TrackSegmentKind;

/** One stored piece of a day's line: its kind and a Google-encoded polyline (precision 5). */
public record TrackSegmentLine(TrackSegmentKind kind, String polyline) {
}
