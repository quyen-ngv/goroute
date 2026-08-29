package com.ds.goroute.entity;

import com.ds.goroute.type.CheckinPhotoSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One photo on a check-in.
 *
 * <p>{@code capturedAt} and the coordinates are recorded at the moment of capture, not at
 * submit: somebody can write their caption an hour later and half a city away. They are
 * reference information only -- the device clock and any embedded metadata are editable,
 * so the server keeps its own received-at time and computes the reward itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCheckinPhoto {
    private UUID id;
    private UUID checkinId;
    private String url;
    private CheckinPhotoSource source;
    private Integer position;
    private LocalDateTime capturedAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracyMeters;
    private LocalDateTime createdAt;
}
