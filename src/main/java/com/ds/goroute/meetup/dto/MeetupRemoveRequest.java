package com.ds.goroute.meetup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code POST /v1/api/admin/meetups/{id}/remove}. Operator text, sent to the host. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupRemoveRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
