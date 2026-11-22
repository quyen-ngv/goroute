package com.ds.goroute.service;

import com.ds.goroute.entity.UserStarWallet;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.StarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private final StarService service = new StarService(starMapper, bootstrap);

    private final UUID userId = UUID.randomUUID();

    private UserStarWallet wallet(int balance) {
        return UserStarWallet.builder().userId(userId).balance(balance).freeTripQuotaUsed(0).build();
    }

    @BeforeEach
    void noEntryRecordedYet() {
        when(starMapper.countReference(anyString())).thenReturn(0);
    }

    @Test
    void aSpendTakesTheStarsUnderTheLock() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(20));
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(20));

        service.spend(userId, 5, "STAR_EXCHANGE", "star_exchange:x", "reason");

        verify(starMapper).incrementBalance(userId, -5);
    }

    @Test
    void aSpendIsRefusedWhenTheBalanceIsShort() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(4));
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(4));

        assertThatThrownBy(() -> service.spend(userId, 5, "STAR_EXCHANGE", "star_exchange:x", "reason"))
                .isInstanceOf(BusinessException.class);
        verify(starMapper, never()).incrementBalance(eq(userId), anyInt());
    }

    @Test
    void aGrantUnderAKeyThatWasAlreadyRecordedIsANoOp() {
        when(starMapper.findWallet(userId)).thenReturn(wallet(0));
        when(starMapper.findWalletForUpdate(userId)).thenReturn(wallet(0));
        when(starMapper.countReference("checkin:abc")).thenReturn(1);

        assertThat(service.grant(userId, 5, "CHECKIN_EXPLORER_POINTS", "checkin:abc", "reason")).isFalse();
        verify(starMapper, never()).insertTransaction(any());
        verify(starMapper, never()).incrementBalance(eq(userId), anyInt());
    }

    @Test
    void whenUserHasNoWalletYet_ensureWalletBootstrapsAndReadsTheCommittedWallet() {
        // The first lookup is cached as empty by the active MyBatis session. The fresh mapper
        // statement runs after the REQUIRES_NEW bootstrap transaction has committed the row.
        when(starMapper.findWallet(userId)).thenReturn(null);
        when(starMapper.findWalletAfterBootstrap(userId)).thenReturn(wallet(0));

        assertThat(service.getBalance(userId)).isZero();

        verify(bootstrap).ensureExists(userId);
        verify(starMapper).findWalletAfterBootstrap(userId);
    }

    @Test
    void whenWalletRemainsNullAfterBootstrap_throwsExceptionInsteadOfNPE() {
        when(starMapper.findWallet(userId)).thenReturn(null);
        when(starMapper.findWalletAfterBootstrap(userId)).thenReturn(null);

        assertThatThrownBy(() -> service.getBalance(userId))
                .isInstanceOf(BusinessException.class);

        verify(bootstrap).ensureExists(userId);
        verify(starMapper).findWalletAfterBootstrap(userId);
    }
}
