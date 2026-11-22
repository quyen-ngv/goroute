package com.ds.goroute.service.place;

import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What follows a review once it is committed (plan §3.8 step 8): the place is reindexed and the
 * search caches cleared, the submitter is rewarded (approved only, at most
 * PLACE_EDIT/MAX_REWARDS_PER_DAY a day, once per suggestion by the Star reference key) and told.
 *
 * <p>Same reasoning as MeetupNotifier: none of it may undo the review, and the search index must
 * not read the place before the review's writes are visible, so it all runs after commit, each
 * step on its own and in a transaction of its own; a failure is logged and dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceEditNotifier {

    public static final String STAR_TYPE = "PLACE_EDIT";
    static final String DEEP_LINK_PREFIX = "/place/";

    private final PlaceService placeService;
    private final StarService starService;
    private final BusinessConfigService config;
    private final PlaceEditSuggestionRepository suggestions;
    private final UserRepository users;
    private final NotificationTemplateRenderer templateRenderer;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    /** One reviewed suggestion; {@code applied} when at least one change was written to the place. */
    public void afterReview(UUID suggestionId, UUID placeId, UUID submitterId, boolean applied,
                            String rejectReason, String fallbackPlaceName, List<PlaceTranslation> translations,
                            UUID reviewerId) {
        List<Runnable> steps = List.of(
                () -> {
                    if (applied) {
                        placeService.reindexPlace(placeId);
                        placeService.evictSearchCache();
                    }
                },
                () -> {
                    if (applied) {
                        reward(submitterId, suggestionId);
                    }
                },
                () -> notifySubmitter(suggestionId, placeId, submitterId, applied, rejectReason,
                        fallbackPlaceName, translations, reviewerId));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            steps.forEach(step -> runLogged(step, suggestionId, false));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                steps.forEach(step -> runLogged(step, suggestionId, true));
            }
        });
    }

    void reward(UUID userId, UUID suggestionId) {
        int stars = config.getInt(BusinessConfigKey.PLACE_EDIT_REWARD_STARS);
        if (stars <= 0) {
            return;
        }
        if (suggestions.countRewardsSince(userId, LocalDateTime.now().minusHours(24))
                >= config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_REWARDS_PER_DAY)) {
            return;
        }
        starService.grant(userId, stars, STAR_TYPE, "place_edit:" + suggestionId,
                "Your place edit suggestion was approved");
    }

    void notifySubmitter(UUID suggestionId, UUID placeId, UUID submitterId, boolean applied, String rejectReason,
                         String fallbackPlaceName, List<PlaceTranslation> translations, UUID reviewerId) {
        String language = users.findById(submitterId).map(User::getLanguage).orElse(null);
        String placeName = PlaceEditResponseAssembler.placeName(null, translations, language);
        Map<String, Object> data = new HashMap<>();
        data.put("placeId", placeId.toString());
        data.put("suggestionId", suggestionId.toString());
        data.put("placeName", placeName != null ? placeName : (fallbackPlaceName == null ? "" : fallbackPlaceName));
        data.put("rejectReason", rejectReason == null ? "" : rejectReason);
        data.put("deepLink", DEEP_LINK_PREFIX + placeId);
        NotificationType type = applied ? NotificationType.PLACE_EDIT_APPROVED : NotificationType.PLACE_EDIT_REJECTED;
        NotificationMessage message = templateRenderer.render(type, data, language);
        notificationService.createNotification(submitterId, null, type, message.title(), message.body(),
                data, reviewerId);
    }

    private void runLogged(Runnable step, UUID suggestionId, boolean ownTransaction) {
        try {
            if (ownTransaction) {
                TransactionTemplate own = new TransactionTemplate(transactionManager);
                own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                own.executeWithoutResult(status -> step.run());
            } else {
                step.run();
            }
        } catch (RuntimeException exception) {
            log.warn("After-review step failed for place edit suggestion {}: {}", suggestionId,
                    exception.getMessage());
        }
    }
}
