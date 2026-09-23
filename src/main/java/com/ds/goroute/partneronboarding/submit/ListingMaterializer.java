package com.ds.goroute.partneronboarding.submit;

import com.ds.goroute.partneronboarding.domain.DraftData;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;

import java.util.UUID;

/**
 * Turns one finished draft into real marketplace rows.
 *
 * <p>One implementation per {@link ListingKind}, chosen at submit time. A fourth kind of
 * listing is then a new class and an enum constant, and neither the service nor the
 * controller learns about it — which is the whole reason the submit path is not a switch.
 *
 * <p>Implementations write through the existing partner services, never through mappers of
 * their own: the wizard must produce exactly what the console produces, including the
 * authorization checks, history entries and inventory side effects that come with them.
 */
public interface ListingMaterializer {

    ListingKind kind();

    /**
     * @return the listing that was created; runs inside the caller's transaction, so throwing
     *         leaves no half-built listing behind
     */
    SubmitResultResponse materialize(Context context);

    /**
     * Everything a materializer may read. Organization defaults are passed in rather than
     * re-fetched so that every branch fills the same gaps the same way.
     *
     * @param organizationTimezone the business's own clock; check-in times and slots mean
     *                             nothing without it
     */
    record Context(UUID actorUserId,
                   UUID draftId,
                   UUID organizationId,
                   String organizationTimezone,
                   String defaultCurrency,
                   DraftData data) {
    }
}
