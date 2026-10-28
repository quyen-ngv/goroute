package com.ds.goroute.service.assistant;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.AssistantThreadRow;
import com.ds.goroute.entity.MarketplaceConversation;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceChatRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceChatService;
import com.ds.goroute.service.TripAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantConversationServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Mock private MarketplaceChatRepository chatRepository;
    @Mock private MarketplaceChatService chatService;
    @Mock private AssistantAccessService access;
    @Mock private UserRepository users;
    @Mock private TripAccessGuard tripAccessGuard;

    private AssistantConversationService service;

    @BeforeEach
    void setUp() {
        service = new AssistantConversationService(chatRepository, chatService, access, users, tripAccessGuard);
        when(access.isAvailable(OWNER)).thenReturn(true);
        when(chatService.get(eq(OWNER), any())).thenAnswer(call ->
                MarketplaceConversationResponse.builder().id(call.getArgument(1)).build());
    }

    @Test
    void aThreadActiveWithinTheWindowIsResumedUnderTheOwnersLock() {
        UUID recent = UUID.randomUUID();
        when(chatRepository.findRecentUsedAssistantThread(eq(OWNER), any())).thenReturn(Optional.of(recent));

        assertThat(service.open(OWNER, 6).getId()).isEqualTo(recent);

        InOrder order = inOrder(chatRepository);
        order.verify(chatRepository).lockAssistantOwner(OWNER);
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        order.verify(chatRepository).findRecentUsedAssistantThread(eq(OWNER), since.capture());
        assertThat(since.getValue()).isBefore(LocalDateTime.now().minusHours(5));
        verify(chatRepository, never()).insertConversation(any());
    }

    @Test
    void anOldThreadIsNotResumedWhenAnUnusedOneIsWaiting() {
        UUID unused = UUID.randomUUID();
        when(chatRepository.findRecentUsedAssistantThread(eq(OWNER), any())).thenReturn(Optional.empty());
        when(chatRepository.findNewestUnusedAssistantThread(OWNER)).thenReturn(Optional.of(unused));

        assertThat(service.open(OWNER, 6).getId()).isEqualTo(unused);
        verify(chatRepository, never()).insertConversation(any());
    }

    @Test
    void aNewThreadIsIntroducedOnlyWhenItIsThePersonsFirst() {
        when(chatRepository.findRecentUsedAssistantThread(eq(OWNER), any())).thenReturn(Optional.empty());
        when(chatRepository.findNewestUnusedAssistantThread(OWNER)).thenReturn(Optional.empty());
        when(chatRepository.hasAssistantThread(OWNER)).thenReturn(true);

        service.open(OWNER, 6);

        ArgumentCaptor<MarketplaceConversation> created = ArgumentCaptor.forClass(MarketplaceConversation.class);
        verify(chatRepository).insertConversation(created.capture());
        assertThat(created.getValue().getAssistantUserId()).isEqualTo(OWNER);
        verify(chatRepository).addMember(eq(created.getValue().getId()), eq(OWNER), eq("CREATOR"), any());
        verify(chatService, never()).postAssistantMessage(any(), any(), anyList(), any(), any(), anyString());
    }

    @Test
    void theFirstThreadEverStartsWithTheIntroduction() {
        when(chatRepository.findByAssistantUser(OWNER, OWNER)).thenReturn(Optional.empty());

        service.open(OWNER, null);

        verify(chatRepository).lockAssistantOwner(OWNER);
        verify(chatService).postAssistantMessage(any(), anyString(), anyList(), any(), any(), eq("intro:" + OWNER));
    }

    @Test
    void newChatReusesTheUnusedThread() {
        UUID unused = UUID.randomUUID();
        when(chatRepository.findNewestUnusedAssistantThread(OWNER)).thenReturn(Optional.of(unused));

        assertThat(service.createThread(OWNER).getId()).isEqualTo(unused);
        verify(chatRepository).lockAssistantOwner(OWNER);
        verify(chatRepository, never()).insertConversation(any());
    }

    @Test
    void anotherPersonsThreadCannotBeRenamedOrDeleted() {
        UUID foreign = UUID.randomUUID();
        when(chatRepository.renameAssistantThread(eq(foreign), eq(OWNER), any(), any())).thenReturn(0);
        when(chatRepository.archiveAssistantThread(eq(foreign), eq(OWNER), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rename(OWNER, foreign, "Lẩu bò"))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.NOT_FOUND));
        assertThatThrownBy(() -> service.archive(OWNER, foreign))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.NOT_FOUND));
    }

    @Test
    void aTitleIsTrimmedAndBoundedBeforeItIsStored() {
        UUID thread = UUID.randomUUID();
        when(chatRepository.renameAssistantThread(eq(thread), eq(OWNER), any(), any())).thenReturn(1);

        service.rename(OWNER, thread, "  Lẩu bò   Cầu Giấy  ");

        verify(chatRepository).renameAssistantThread(eq(thread), eq(OWNER), eq("Lẩu bò Cầu Giấy"), any());
        assertThatThrownBy(() -> service.rename(OWNER, thread, "x".repeat(81)))
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.INVALID_PARAMETERS));
    }

    @Test
    void aTripTheOwnerCanNoLongerReadIsLeftOffTheThreadRow() {
        UUID kept = UUID.randomUUID();
        UUID lost = UUID.randomUUID();
        when(chatRepository.findAssistantThreads(OWNER, "lẩu", 20, 0)).thenReturn(List.of(
                AssistantThreadRow.builder().id(UUID.randomUUID()).title("Lẩu bò").tripId(kept).tripName("Hà Nội").build(),
                AssistantThreadRow.builder().id(UUID.randomUUID()).title("Lẩu cá").tripId(lost).tripName("Huế").build()));
        when(chatRepository.countAssistantThreads(OWNER, "lẩu")).thenReturn(2L);
        when(tripAccessGuard.canRead(kept, OWNER)).thenReturn(true);
        when(tripAccessGuard.canRead(lost, OWNER)).thenReturn(false);

        var page = service.listThreads(OWNER, " lẩu ", 0, 20);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getItems().get(0).getTripName()).isEqualTo("Hà Nội");
        assertThat(page.getItems().get(1).getTripId()).isNull();
        assertThat(page.getItems().get(1).getUnreadCount()).isZero();
    }
}
