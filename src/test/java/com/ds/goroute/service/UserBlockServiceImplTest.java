package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.UserBlockResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserBlock;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.UserBlockMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.impl.UserBlockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Blocking is a safety feature, so the tests are about what it refuses and what it keeps
 * private, not about the happy path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserBlockService")
class UserBlockServiceImplTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Mock
    private UserBlockMapper blocks;

    @Mock
    private UserRepository users;

    private UserBlockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserBlockServiceImpl(blocks, users);
        when(users.findById(BOB)).thenReturn(Optional.of(user(BOB, "Bob")));
    }

    @Nested
    @DisplayName("when blocking")
    class WhenBlocking {

        @Test
        @DisplayName("records the decision and answers with who it is about")
        void records() {
            UserBlockResponse response = service.block(ALICE, BOB, "  kept sending spam  ");

            ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
            verify(blocks).insert(saved.capture());
            assertThat(saved.getValue())
                    .extracting(UserBlock::getBlockerId, UserBlock::getBlockedId, UserBlock::getReason)
                    .containsExactly(ALICE, BOB, "kept sending spam");
            assertThat(response.getUserId()).isEqualTo(BOB);
            assertThat(response.getFullName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("refuses to block yourself")
        void refusesSelf() {
            assertThatThrownBy(() -> service.block(ALICE, ALICE, null))
                    .isInstanceOf(BusinessException.class);
            verify(blocks, never()).insert(any());
        }

        @Test
        @DisplayName("refuses somebody who does not exist")
        void refusesUnknown() {
            UUID ghost = UUID.randomUUID();
            when(users.findById(ghost)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.block(ALICE, ghost, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getError().getCode())
                    .isEqualTo(ErrorConstant.NOT_FOUND);
        }

        @Test
        @DisplayName("a blank reason is stored as nothing, not as whitespace")
        void blankReason() {
            service.block(ALICE, BOB, "   ");

            ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
            verify(blocks).insert(saved.capture());
            assertThat(saved.getValue().getReason()).isNull();
        }

        @Test
        @DisplayName("a very long reason is cut rather than refused")
        void longReason() {
            service.block(ALICE, BOB, "x".repeat(900));

            ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
            verify(blocks).insert(saved.capture());
            assertThat(saved.getValue().getReason()).hasSize(500);
        }
    }

    @Nested
    @DisplayName("when asked whether two people may talk")
    class WhenAsked {

        @Test
        @DisplayName("either direction counts")
        void eitherDirection() {
            when(blocks.existsBetween(ALICE, BOB)).thenReturn(true);

            assertThat(service.blockedBetween(ALICE, BOB)).isTrue();
        }

        @Test
        @DisplayName("a person is never blocked from themselves")
        void neverSelf() {
            assertThat(service.blockedBetween(ALICE, ALICE)).isFalse();
            verify(blocks, never()).existsBetween(any(), any());
        }

        @Test
        @DisplayName("a missing id is not a block")
        void nullIsNotABlock() {
            assertThat(service.blockedBetween(ALICE, null)).isFalse();
            assertThat(service.blockedBetween(null, BOB)).isFalse();
        }
    }

    @Nested
    @DisplayName("when filtering a list")
    class WhenFiltering {

        @Test
        @DisplayName("asks once for the whole list")
        void oneQuery() {
            UUID carol = UUID.randomUUID();
            when(blocks.selectBlockedAmong(eq(ALICE), anyCollection())).thenReturn(List.of(BOB));

            Set<UUID> blocked = service.blockedAmong(ALICE, List.of(BOB, carol));

            assertThat(blocked).containsExactly(BOB);
            verify(blocks).selectBlockedAmong(eq(ALICE), anyCollection());
        }

        @Test
        @DisplayName("the viewer is never in their own candidate list")
        void dropsSelf() {
            // A conversation's participant list includes the sender, and asking the
            // database whether somebody blocked themselves is a query that can only
            // return nothing.
            assertThat(service.blockedAmong(ALICE, List.of(ALICE))).isEmpty();
            verify(blocks, never()).selectBlockedAmong(any(), anyCollection());
        }

        @Test
        @DisplayName("an empty list costs no query")
        void noQueryForEmpty() {
            assertThat(service.blockedAmong(ALICE, List.of())).isEmpty();
            verify(blocks, never()).selectBlockedAmong(any(), anyCollection());
        }
    }

    @Nested
    @DisplayName("when listing")
    class WhenListing {

        @Test
        @DisplayName("a blocked account that has since gone still shows")
        void deletedAccountStillShows() {
            UUID gone = UUID.randomUUID();
            when(users.findById(gone)).thenReturn(Optional.empty());
            when(blocks.selectByBlocker(ALICE)).thenReturn(List.of(
                    UserBlock.builder().blockerId(ALICE).blockedId(gone)
                            .reason("why").createdAt(LocalDateTime.now()).build()));

            List<UserBlockResponse> listed = service.listBlocked(ALICE);

            // Otherwise the list would be shorter than the count and the entry could
            // never be removed.
            assertThat(listed).hasSize(1);
            assertThat(listed.getFirst().getUserId()).isEqualTo(gone);
            assertThat(listed.getFirst().getFullName()).isNull();
        }
    }

    @Test
    @DisplayName("unblocking somebody who was never blocked is not an error")
    void unblockIsIdempotent() {
        service.unblock(ALICE, BOB);

        verify(blocks).delete(ALICE, BOB);
    }

    private User user(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        return user;
    }
}
