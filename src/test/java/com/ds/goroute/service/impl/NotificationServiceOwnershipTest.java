package com.ds.goroute.service.impl;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.UserDeviceMapper;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.service.notification.NotificationPayloadFactory;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceOwnershipTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(
            repository,
            mock(UserRepository.class),
            mock(FirebaseService.class),
            mock(NotificationPayloadFactory.class),
            mock(UserDeviceMapper.class),
            new Gson());

    @Test
    void notificationReadsUseDatabasePaginationAndUserScope() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        when(repository.findPageByUserId(userId, tripId, true, 25, 50)).thenReturn(List.of());

        assertThat(service.getNotifications(userId, 2, 25, true, tripId)).isEmpty();

        verify(repository).findPageByUserId(userId, tripId, true, 25, 50);
    }

    @Test
    void markAsReadCannotModifyAnotherUsersNotification() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(repository.markAsRead(notificationId, userId)).thenReturn(0);

        assertThatThrownBy(() -> service.markAsRead(userId, notificationId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Notification not found");
    }
}
