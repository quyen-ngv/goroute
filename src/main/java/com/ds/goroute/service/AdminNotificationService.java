package com.ds.goroute.service;

import com.ds.goroute.dto.request.AdminBroadcastNotificationRequest;
import com.ds.goroute.dto.request.AdminNotificationAudienceRequest;
import com.ds.goroute.dto.response.AdminNotificationAudienceResponse;
import com.ds.goroute.dto.response.AdminNotificationHistoryResponse;
import com.ds.goroute.dto.response.AdminNotificationRecipientResponse;
import com.ds.goroute.dto.response.AdminPushNotificationResponse;
import com.ds.goroute.dto.response.PageResponse;

/** Console-side announcements: who would receive one, sending it, and what was sent before. */
public interface AdminNotificationService {

    /** Resolves a segment without sending anything, so the operator can see the reach first. */
    AdminNotificationAudienceResponse previewAudience(AdminNotificationAudienceRequest request, int sampleSize);

    AdminPushNotificationResponse broadcast(AdminBroadcastNotificationRequest request);

    /** The picker behind "send to these people": matches username, full name and e-mail. */
    PageResponse<AdminNotificationRecipientResponse> searchRecipients(String search, int page, int size);

    PageResponse<AdminNotificationHistoryResponse> history(String search, String sort, boolean descending, int page, int size);
}
