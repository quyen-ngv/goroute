package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpdateUserHomeRequest;
import com.ds.goroute.dto.response.UserHomeResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserHomeServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserHomeService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("A user who has not said where they are from gets an empty answer")
    void unknownHomeIsEmpty() {
        when(userRepository.findHomeCountryCode(userId)).thenReturn(null);

        UserHomeResponse home = service.getHome(userId, Locale.ENGLISH);

        assertThat(home.getCountryCode()).isNull();
        assertThat(home.getCountryName()).isNull();
    }

    @Test
    @DisplayName("The country is stored upper-case and named in the request language")
    void savesTheCountryAndNamesIt() {
        UpdateUserHomeRequest request = new UpdateUserHomeRequest();
        request.setCountryCode("jp");
        when(userRepository.updateHomeCountryCode(eq(userId), eq("JP"), any())).thenReturn(1);

        UserHomeResponse home = service.updateHome(userId, request, Locale.forLanguageTag("vi"));

        assertThat(home.getCountryCode()).isEqualTo("JP");
        assertThat(home.getCountryName()).isEqualTo("Nhật Bản");
    }

    @Test
    @DisplayName("A code that is not an ISO country is refused before anything is written")
    void refusesAnUnknownCountry() {
        UpdateUserHomeRequest request = new UpdateUserHomeRequest();
        request.setCountryCode("ZZ");

        assertThatThrownBy(() -> service.updateHome(userId, request, Locale.ENGLISH))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Unknown country code");
        verify(userRepository, never()).updateHomeCountryCode(any(), any(), any());
    }
}
