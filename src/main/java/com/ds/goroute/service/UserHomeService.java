package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdateUserHomeRequest;
import com.ds.goroute.dto.response.UserHomeResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Where a user is from: their country. A destination picker reads it to list the user's own
 * country region by region instead of as one entry.
 */
@Service
@RequiredArgsConstructor
public class UserHomeService {

    private static final Set<String> ISO_COUNTRY_CODES = Set.of(Locale.getISOCountries());

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserHomeResponse getHome(UUID userId, Locale locale) {
        return toResponse(userRepository.findHomeCountryCode(userId), locale);
    }

    @Transactional
    public UserHomeResponse updateHome(UUID userId, UpdateUserHomeRequest request, Locale locale) {
        String countryCode = request.getCountryCode().trim().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRY_CODES.contains(countryCode)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Unknown country code");
        }
        if (userRepository.updateHomeCountryCode(userId, countryCode, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found");
        }
        return toResponse(countryCode, locale);
    }

    private static UserHomeResponse toResponse(String countryCode, Locale locale) {
        Locale target = locale == null || locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
        String countryName = countryCode == null ? null : Locale.of("", countryCode).getDisplayCountry(target);
        return UserHomeResponse.builder()
                .countryCode(countryCode)
                .countryName(countryName == null || countryName.isBlank() ? null : countryName)
                .build();
    }
}
