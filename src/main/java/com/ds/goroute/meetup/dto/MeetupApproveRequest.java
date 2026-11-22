package com.ds.goroute.meetup.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code POST /v1/api/admin/meetups/{id}/approve}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupApproveRequest {

    @NotNull
    private Long expectedVersion;
}
