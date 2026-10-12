package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.type.MarketplaceConversationStatus;
import com.ds.goroute.type.MarketplaceConversationType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The person's own 1:1 chat with Pengoo: created on first open, one per person (unique index),
 * private like any person-to-person chat. Pengoo opens it with a short introduction.
 */
@Service
@RequiredArgsConstructor
public class AssistantConversationService {

    private final MarketplaceChatRepository chatRepository;
    private final MarketplaceChatService chatService;
    private final AssistantAccessService access;
    private final UserRepository users;

    @Transactional
    public MarketplaceConversationResponse open(UUID userId) {
        if (!access.isAvailable(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Pengoo is not available for this account yet");
        }
        MarketplaceConversation conversation = chatRepository.findByAssistantUser(userId, userId)
                .orElseGet(() -> create(userId));
        return chatService.get(userId, conversation.getId());
    }

    private MarketplaceConversation create(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        MarketplaceConversation created = MarketplaceConversation.builder()
                .id(UUID.randomUUID())
                .conversationType(MarketplaceConversationType.ASSISTANT.name())
                .assistantUserId(userId)
                .status(MarketplaceConversationStatus.OPEN.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            chatRepository.insertConversation(created);
        } catch (DataIntegrityViolationException raced) {
            return chatRepository.findByAssistantUser(userId, userId).orElseThrow(() -> raced);
        }
        chatRepository.addMember(created.getId(), userId, "CREATOR", now);
        String language = users.findById(userId).map(User::getLanguage).orElse("vi");
        chatService.postAssistantMessage(created.getId(), AssistantNotices.directIntro(language),
                List.of(Map.of("kind", "SUGGESTIONS", "items", AssistantNotices.directSuggestions(language))),
                null, null, "intro:" + userId);
        return created;
    }
}
