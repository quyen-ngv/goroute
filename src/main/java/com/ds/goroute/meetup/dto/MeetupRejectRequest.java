package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupRejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code POST /v1/api/admin/meetups/{id}/reject}. Operator text, sent to the host only; a note is
 * required when the reason is {@code OTHER}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupRejectRequest {

    @NotNull
    private MeetupRejectReason reasonCode;

    @Size(max = 500)
    private String note;

    @NotNull
    private Long expectedVersion;
}
