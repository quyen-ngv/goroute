package com.ds.goroute.quest.service;

import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.quest.dto.CreateQuestRequest;
import com.ds.goroute.quest.dto.QuestDraftResponse;
import com.ds.goroute.quest.dto.QuestSummaryResponse;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;

import java.util.UUID;

/**
 * The dual builder's backend (D15): the app and the console read and write the same draft through
 * this one service. Draft mechanics follow partner-listing v2 — a draft is a real DRAFT record,
 * every save PUTs the whole record with an expected version, and "step done" is derived from the
 * data, never a stored flag.
 */
public interface QuestBuilderService {

    QuestDraftResponse create(UUID actorUserId, CreateQuestRequest request, boolean allowSystemOrigin);

    QuestDraftResponse getDraft(UUID questId, UUID actorUserId, boolean admin);

    QuestDraftResponse saveDraft(UUID questId, UUID actorUserId, SaveQuestDraftRequest request);

    /**
     * @param enforceCreatorGate the app builder passes {@code true} (feature flag, cooldown and
     *     quotas apply); the console/operator path passes {@code false}.
     */
    QuestDraftResponse submit(UUID questId, UUID actorUserId, Long expectedVersion, boolean enforceCreatorGate);

    PageResponse<QuestSummaryResponse> listMine(UUID actorUserId, String status, int page, int size);
}
