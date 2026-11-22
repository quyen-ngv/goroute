package com.ds.goroute.event;

import com.ds.goroute.service.assistant.AssistantContextBuilder;
import com.ds.goroute.service.assistant.AssistantTurnService;
import com.ds.goroute.service.assistant.AssistantWelcomeService;
import com.ds.goroute.service.assistant.AssistantWorkerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pengoo's reactions, all after commit and off the request thread: a message that may ask it
 * something, a turn ready to run, a trip chat to greet. A failure here never touches the chat
 * message or the trip that caused it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantEventListener {

    private final AssistantTurnService turnService;
    private final AssistantContextBuilder contextBuilder;
    private final AssistantWorkerClient workerClient;
    private final AssistantWelcomeService welcomeService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatMessage(ChatMessageCreatedEvent event) {
        try {
            turnService.onMessage(event);
        } catch (RuntimeException e) {
            log.warn("Assistant: could not handle message {}: {}", event.messageId(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDispatch(AssistantTurnDispatchEvent event) {
        try {
            workerClient.start(contextBuilder.build(event.turnId()));
        } catch (RuntimeException e) {
            log.warn("Assistant: could not hand turn {} to the worker: {}", event.turnId(), e.getMessage());
            try {
                turnService.fail(event.turnId(), "dispatch: " + e.getMessage());
            } catch (RuntimeException failure) {
                log.warn("Assistant: could not fail turn {}: {}", event.turnId(), failure.getMessage());
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripConversationCreated(TripConversationCreatedEvent event) {
        try {
            welcomeService.greetTrip(event.conversationId(), event.tripId());
        } catch (RuntimeException e) {
            log.warn("Assistant: could not greet trip chat {}: {}", event.conversationId(), e.getMessage());
        }
    }
}
