package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.RegisterDeviceRequest;
import com.ds.goroute.dto.request.UpdateDeviceRequest;
import com.ds.goroute.entity.UserDevice;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.UserDeviceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserDeviceService")
class UserDeviceServiceImplTest {

    private final UserDeviceMapper mapper = mock(UserDeviceMapper.class);
    private final UserDeviceServiceImpl service = new UserDeviceServiceImpl(mapper);

    @Test
    @DisplayName("registers a token for whoever registered it last, taking it from anyone else")
    void takesTheTokenOver() {
        UUID me = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        when(mapper.upsertByToken(any())).thenAnswer(invocation -> {
            UserDevice candidate = invocation.getArgument(0);
            candidate.setId(existingId);
            return candidate;
        });

        var response = service.register(me, RegisterDeviceRequest.builder()
                .fcmToken("token-1").deviceType("android").language("vi").build());

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(mapper).upsertByToken(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(me);
        assertThat(captor.getValue().getFcmToken()).isEqualTo("token-1");
        assertThat(captor.getValue().getIsActive()).isTrue();
        assertThat(response.getId()).isEqualTo(existingId);
    }

    @Test
    @DisplayName("frees a token moved onto a device by an update")
    void updateKeepsOneOwner() {
        UUID me = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        when(mapper.updateDevice(eq(device), eq(me), eq("token-2"), any(), any())).thenReturn(1);
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setFcmToken("token-2");

        service.update(me, device, request);

        verify(mapper).deleteByTokenExceptDevice("token-2", device);
    }

    @Test
    @DisplayName("refuses to update somebody else's device")
    void updateIsOwnerScoped() {
        when(mapper.updateDevice(any(), any(), anyString(), any(), any())).thenReturn(0);
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setFcmToken("token-2");

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("forgets every device of an account being deleted")
    void deletesAllForUser() {
        UUID me = UUID.randomUUID();

        service.deleteAllForUser(me);

        verify(mapper).deleteByUserId(me);
    }
}
