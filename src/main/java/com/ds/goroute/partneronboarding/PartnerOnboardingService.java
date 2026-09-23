package com.ds.goroute.partneronboarding;

import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.partneronboarding.domain.DraftStatus;
import com.ds.goroute.partneronboarding.dto.CreateDraftRequest;
import com.ds.goroute.partneronboarding.dto.DraftResponse;
import com.ds.goroute.partneronboarding.dto.DraftSummaryResponse;
import com.ds.goroute.partneronboarding.dto.OnboardingMeResponse;
import com.ds.goroute.partneronboarding.dto.SaveStepRequest;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.service.ImageUploadOutcome;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The listing wizard's server side: a place to keep answers between steps, and one act that
 * turns them into a real listing.
 *
 * <p>Everything a listing needs after that — going on sale, rooms two and three, the price
 * calendar — belongs to the marketplace services and the workspace that already own it. This
 * service deliberately stops at creation.
 */
public interface PartnerOnboardingService {

    /** Whether to offer a partner entry point at all, and what is already in progress. */
    OnboardingMeResponse me(UUID actorUserId);

    DraftResponse create(UUID actorUserId, CreateDraftRequest request);

    PageResponse<DraftSummaryResponse> list(UUID actorUserId, DraftStatus status, int page, int size);

    DraftResponse get(UUID actorUserId, UUID draftId);

    /**
     * Stores one step's answers. The {@code organization} step additionally creates or
     * attaches the business, because every later step — media upload included — needs one.
     */
    DraftResponse saveStep(UUID actorUserId, UUID draftId, String stepCode, SaveStepRequest request);

    /** Photos for the listing being drafted; each file succeeds or fails on its own. */
    List<ImageUploadOutcome> uploadMedia(UUID actorUserId, UUID draftId, List<MultipartFile> files);

    /**
     * Creates the listing. One transaction: a failure part-way leaves no half-built property
     * behind, and the draft stays editable so the partner can correct and retry.
     */
    SubmitResultResponse submit(UUID actorUserId, UUID draftId, Long expectedVersion);

    /** Gives up on a draft. The row stays as the record that an attempt was made. */
    void abandon(UUID actorUserId, UUID draftId, Long expectedVersion);
}
