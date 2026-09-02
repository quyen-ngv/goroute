package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What a segment currently resolves to. {@code deliverableCount} is the number the operator
 * should trust: the rest have no registered device and will only ever see the in-app row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationAudienceResponse {

    private long totalCount;
    private long deliverableCount;
    private List<AdminNotificationRecipientResponse> sample;
}
