package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AssistantThreadResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.AssistantThreadRow;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The person's own 1:1 threads with Pengoo, private like any person-to-person chat. A person may
 * keep several (V261): each has its own title and notes, deleting one archives it, and the
 * profile ({@link AssistantProfileService}) is shared by all of them. Pengoo opens the first
 * thread a person ever has with a short introduction; later threads start empty.
 *
 * <p>Every path that may create a thread takes the owner's advisory lock first, so two opens at
 * once still make one thread.
 */
@Service
@RequiredArgsConstructor
public class AssistantConversationService {

    static final int MAX_TITLE = 80;

    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceChatService chatService;
    private final AssistantAccessService access;
    private final UserRepository users;
    private final TripAccessGuard tripAccessGuard;

    /**
     * The thread to open from the inbox or the Pengoo bubble, created when there is none.
     *
     * @param resumeWithinHours null: the latest live thread (as before threads existed). Set: the
     *                          latest thread the person wrote in, if its last message is that recent;
     *                          else their newest unused thread; else a new one, introduced by Pengoo
     *                          only when it is the person's first thread ever
     */
    @Transactional
    public MarketplaceConversationResponse open(UUID userId, Integer resumeWithinHours) {
        requireAvailable(userId);
        chatRepository.lockAssistantOwner(userId);
        UUID conversationId;
        if (resumeWithinHours == null) {
            conversationId = chatRepository.findByAssistantUser(userId, userId)
                    .map(MarketplaceConversation::getId)
                    .orElseGet(() -> create(userId, true));
        } else {
            LocalDateTime since = LocalDateTime.now().minusHours(resumeWithinHours);
            conversationId = chatRepository.findRecentUsedAssistantThread(userId, since)
                    .or(() -> chatRepository.findNewestUnusedAssistantThread(userId))
                    .orElseGet(() -> create(userId, !chatRepository.hasAssistantThread(userId)));
        }
        return chatService.get(userId, conversationId);
    }

    /** "New chat": the newest unused thread when there is one, so tapping twice does not make two. */
    @Transactional
    public MarketplaceConversationResponse createThread(UUID userId) {
        requireAvailable(userId);
        chatRepository.lockAssistantOwner(userId);
        UUID conversationId = chatRepository.findNewestUnusedAssistantThread(userId)
                .orElseGet(() -> create(userId, false));
        return chatService.get(userId, conversationId);
    }

    /**
     * The person's live threads they wrote in, latest first. A thread's trip is the one its latest
     * applied proposal changed or created, shown only while the person may still read that trip.
     */
    @Transactional(readOnly = true)
    public PageResponse<AssistantThreadResponse> listThreads(UUID userId, String query, int page, int size) {
        String q = query == null || query.isBlank() ? null : query.trim();
        List<AssistantThreadRow> rows = chatRepository.findAssistantThreads(userId, q, size, page * size);
        long total = rows.isEmpty() && page == 0 ? 0 : chatRepository.countAssistantThreads(userId, q);
        Map<UUID, Boolean> readable = new HashMap<>();
        List<AssistantThreadResponse> items = rows.stream().map(row -> {
            boolean showTrip = row.getTripId() != null && row.getTripName() != null
                    && readable.computeIfAbsent(row.getTripId(), tripId -> tripAccessGuard.canRead(tripId, userId));
            return AssistantThreadResponse.builder()
                    .id(row.getId())
                    .title(row.getTitle())
                    .lastMessagePreview(row.getLastMessagePreview())
                    .lastMessageAt(row.getLastMessageAt())
                    .unreadCount(row.getUnreadCount() == null ? 0L : row.getUnreadCount())
                    .tripId(showTrip ? row.getTripId() : null)
                    .tripName(showTrip ? row.getTripName() : null)
                    .build();
        }).toList();
        return PageResponse.of(items, total, page, size);
    }

    /** The owner names a thread; the chat filter already let the title in (the request is moderated). */
    @Transactional
    public MarketplaceConversationResponse rename(UUID userId, UUID conversationId, String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim().replaceAll("\\s+", " ");
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The title must be 1 to " + MAX_TITLE + " characters");
        }
        if (chatRepository.renameAssistantThread(conversationId, userId, title, LocalDateTime.now()) != 1) {
            throw notFound();
        }
        return chatService.get(userId, conversationId);
    }

    /** Deleting a thread archives it; an answer still being written for it is posted all the same. */
    @Transactional
    public void archive(UUID userId, UUID conversationId) {
        if (chatRepository.archiveAssistantThread(conversationId, userId, LocalDateTime.now()) != 1) {
            throw notFound();
        }
    }

    private void requireAvailable(UUID userId) {
        if (!access.isAvailable(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Pengoo is not available for this account yet");
        }
    }

    /** Called under the owner's lock. */
    private UUID create(UUID userId, boolean introduce) {
        LocalDateTime now = LocalDateTime.now();
        MarketplaceConversation created = MarketplaceConversation.builder()
                .id(UUID.randomUUID())
                .conversationType(MarketplaceConversationType.ASSISTANT.name())
                .assistantUserId(userId)
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        chatRepository.insertConversation(created);
        chatRepository.addMember(created.getId(), userId, "CREATOR", now);
        if (introduce) {
            String language = users.findById(userId).map(User::getLanguage).orElse("vi");
            chatService.postAssistantMessage(created.getId(), AssistantNotices.directIntro(language),
                    List.of(Map.of("kind", "SUGGESTIONS", "items", AssistantNotices.directSuggestions(language))),
                    null, null, "intro:" + userId);
        }
        return created.getId();
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Conversation not found");
    }
}
