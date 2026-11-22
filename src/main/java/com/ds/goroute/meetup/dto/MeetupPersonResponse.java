package com.ds.goroute.meetup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A host or participant as other users see them. Never carries the birth year. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupPersonResponse {

    private UUID id;
    private String name;
    private String avatarUrl;
}
