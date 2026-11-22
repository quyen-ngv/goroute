package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventAttendanceMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The detail fields shared by the public and the console detail. The two differ only in the
 * shape of {@code images}, so each subclass declares its own.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class LocalEventDetailFields extends LocalEventSummaryResponse {

    private String summary;
    private String description;
    private String language;
    private List<String> tags;
    private List<LocalEventSessionResponse> sessions;
    private String scheduleNote;
    private String lunarDateText;
    private LocalDateTime previousStartAt;
    private LocalEventAttendanceMode attendanceMode;
    private String onlineUrl;
    private UUID placeId;
    private String provinceCode;
    private String wardCode;
    private String priceNote;
    private String ticketUrl;
    private String organizerName;
    private String organizerUrl;
    private List<String> performers;
    private Integer minAge;
    /** True when the import had no end time and the end was estimated (start + 3 h or end of day). */
    private Boolean endAtEstimated;
    private String source;
    private String sourceUrl;
    private LocalDateTime updatedAt;
}
