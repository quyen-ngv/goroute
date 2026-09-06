package com.ds.goroute.service;

import com.ds.goroute.dto.response.StarWalletResponse;
import com.ds.goroute.entity.TripCreationEntitlement;
import com.ds.goroute.entity.UserStarWallet;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.StarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StarServiceTest {

    private final StarMapper starMapper = mock(StarMapper.class);
    private final StarWalletBootstrapService bootstrap = mock(StarWalletBootstrapService.class);
    private final UserQuotaPolicyService quotaPolicy = mock(UserQuotaPolicyService.class);
    private final StarService service = new StarService(starMapper, bootstrap, quotaPolicy);

    private final UUID userId = UUID.randomUUID();

    private UserStarWallet wallet(int balance, int freeQuotaUsed) {
        return UserStarWallet.builder()
                .userId(userId).balance(balance).freeTripQuotaUsed(freeQuotaUsed).build();
    }

    @BeforeEach
    void noEntryRecordedYet() {
        when(starMapper.countReference(anyString())).thenReturn(0);
        when(quotaPolicy.freeTripQuota(userId)).thenReturn(3);
    }

    /**
     * The quota bound used to live in the SQL as a literal 2 while the service constant
     * said 3, so a user sitting at 2 was offered a third free trip that nothing granted.
     */
    @Test
    void theFreeQuotaBoundComesFromTheServiceNotFromTheStatement() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 2));
        when(starMapper.incrementFreeQuota(eq(userId), anyInt())).thenReturn(1);

        service.reserveTripCreation(userId);

        verify(starMapper).incrementFreeQuota(userId, 3);
    }

    @Test
    void aUserOutOfFreeSlotsAndOutOfEntitlementsIsRefused() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 3));
        when(starMapper.findActiveEntitlement(eq(userId), any())).thenReturn(null);

        assertThatThrownBy(() -> service.reserveTripCreation(userId))
                .isInstanceOf(BusinessException.class);
        verify(starMapper, never()).incrementFreeQuota(eq(userId), anyInt());
    }

    @Test
    void anUnlockedSlotIsConsumedOnceTheFreeQuotaIsGone() {
        UUID entitlementId = UUID.randomUUID();
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 3));
        when(starMapper.findActiveEntitlement(eq(userId), any()))
                .thenReturn(TripCreationEntitlement.builder().id(entitlementId).userId(userId).build());
        when(starMapper.consumeEntitlement(eq(entitlementId), any())).thenReturn(1);

        service.reserveTripCreation(userId);

        verify(starMapper).consumeEntitlement(eq(entitlementId), any());
    }

    @Test
    void unlockingChargesTheCostAndWritesOneEntitlement() {
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(10, 3));
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 3));
        when(starMapper.countEntitlements(userId)).thenReturn(0);

        StarWalletResponse response = service.unlockTrip(userId);

        verify(starMapper).incrementBalance(userId, -10);
        verify(starMapper).insertEntitlement(any(TripCreationEntitlement.class));
        assertThat(response.getStarsToUnlockTrip()).isEqualTo(10);
    }

    /**
     * The second tap of a double tap. It reaches the same reference key, so it must buy
     * nothing -- the previous random-key version charged twice and issued two slots.
     */
    @Test
    void aReplayedUnlockNeitherChargesAgainNorIssuesASecondSlot() {
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(10, 3));
        when(starMapper.findWallet(userId)).thenReturn(wallet(10, 3));
        when(starMapper.countEntitlements(userId)).thenReturn(0);
        when(starMapper.countReference("trip_unlock:" + userId + ":0")).thenReturn(1);

        service.unlockTrip(userId);

        verify(starMapper, never()).incrementBalance(eq(userId), anyInt());
        verify(starMapper, never()).insertEntitlement(any());
    }

    @Test
    void unlockingIsRefusedWhenTheBalanceIsShort() {
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(9, 3));
        when(starMapper.countEntitlements(userId)).thenReturn(0);

        assertThatThrownBy(() -> service.unlockTrip(userId)).isInstanceOf(BusinessException.class);
        verify(starMapper, never()).insertEntitlement(any());
    }

    /**
     * Having enough stars is not the same as being able to create a trip: the unlock has
     * to be bought first. Reporting the first as the second is what put a green tick in
     * front of users whose next trip creation was about to be refused.
     */
    @Test
    void affordingAnUnlockIsNotTheSameAsBeingAbleToCreateATrip() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(50, 3));
        when(starMapper.countActiveEntitlements(eq(userId), any())).thenReturn(0);
        when(starMapper.findTransactions(eq(userId), anyInt(), anyInt())).thenReturn(List.of());

        StarWalletResponse response = service.getWallet(userId);

        assertThat(response.isCanCreateTrip()).isFalse();
        assertThat(response.getUnlockedTripSlots()).isZero();
    }

    @Test
    void anUnusedUnlockedSlotMakesTripCreationPossible() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 3));
        when(starMapper.countActiveEntitlements(eq(userId), any())).thenReturn(1);
        when(starMapper.findTransactions(eq(userId), anyInt(), anyInt())).thenReturn(List.of());

        StarWalletResponse response = service.getWallet(userId);

        assertThat(response.isCanCreateTrip()).isTrue();
        assertThat(response.getUnlockedTripSlots()).isEqualTo(1);
    }

    @Test
    void aGrantUnderAKeyThatWasAlreadyRecordedIsANoOp() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 0));
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(0, 0));
        when(starMapper.countReference("checkin:abc")).thenReturn(1);

        assertThat(service.grant(userId, 5, "CHECKIN_EXPLORER_POINTS", "checkin:abc", "reason")).isFalse();
        verify(starMapper, never()).insertTransaction(any());
        verify(starMapper, never()).incrementBalance(eq(userId), anyInt());
    }

    @Test
    void expiredEntitlementsDoNotCountAsUnlockedSlots() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(0, 3));
        when(starMapper.countActiveEntitlements(eq(userId), any(LocalDateTime.class))).thenReturn(0);
        when(starMapper.findTransactions(eq(userId), anyInt(), anyInt())).thenReturn(List.of());

        assertThat(service.getWallet(userId).isCanCreateTrip()).isFalse();
    }

    @Test
    void whenUserHasNoWalletYet_ensureWalletBootstrapsAndReadsTheCommittedWallet() {
        // The first lookup is cached as empty by the active MyBatis session. The fresh mapper
        // statement runs after the REQUIRES_NEW bootstrap transaction has committed the row.
        when(starMapper.findWallet(userId)).thenReturn(null);
        when(starMapper.findWalletAfterBootstrap(userId)).thenReturn(wallet(0, 0));
        when(starMapper.incrementFreeQuota(eq(userId), anyInt())).thenReturn(1);

        service.reserveTripCreation(userId);

        verify(bootstrap).ensureExists(userId);
        verify(starMapper).findWalletAfterBootstrap(userId);
        verify(starMapper).incrementFreeQuota(userId, 3);
    }

    @Test
    void whenWalletRemainsNullAfterBootstrap_throwsExceptionInsteadOfNPE() {
        when(starMapper.findWallet(userId)).thenReturn(null);
        when(starMapper.findWalletAfterBootstrap(userId)).thenReturn(null);

        assertThatThrownBy(() -> service.reserveTripCreation(userId))
                .isInstanceOf(BusinessException.class);

        verify(bootstrap).ensureExists(userId);
        verify(starMapper).findWalletAfterBootstrap(userId);
    }
}
