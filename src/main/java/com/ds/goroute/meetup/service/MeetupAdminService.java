package com.ds.goroute.meetup.service;

import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.meetup.dto.AdminMeetupDetail;
import com.ds.goroute.meetup.dto.AdminMeetupRow;
import com.ds.goroute.meetup.dto.MeetupRejectRequest;
import com.ds.goroute.type.MeetupAdminSort;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The console side of meetups (SPEC_V2 §2.5): the review queue, the list, and the decisions. */
public interface MeetupAdminService {

    PageResponse<AdminMeetupRow> list(List<MeetupStatus> statuses, List<MeetupCategory> categories, String search,
                                      String provinceCode, LocalDate dateFrom, LocalDate dateTo, boolean hidePast,
                                      MeetupAdminSort sort, int page, int size);

    AdminMeetupDetail detail(UUID meetupId);

    AdminMeetupDetail approve(UUID adminUserId, UUID meetupId, long expectedVersion);

    AdminMeetupDetail reject(UUID adminUserId, UUID meetupId, MeetupRejectRequest request);

    AdminMeetupDetail remove(UUID adminUserId, UUID meetupId, String reason);
}
