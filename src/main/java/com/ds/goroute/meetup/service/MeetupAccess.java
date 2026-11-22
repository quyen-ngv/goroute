package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Who may use meetups at all, and who may host or join. The feature flag is checked here on the
 * server, never trusted from the client, and beta users pass while it is off (the standing rule
 * for every flagged feature). Age comes from the self-declared birth year.
 */
@Component
@RequiredArgsConstructor
public class MeetupAccess {

    /** Meetups are for adults only; the age-range floor is the same number. */
    public static final int MIN_AGE = 18;

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final MeetupRepository repository;

    public boolean isEnabled(UUID userId) {
        return config.getBoolean(BusinessConfigKey.MEETUP_ENABLED) || betaAccess.isBetaUser(userId);
    }

    /** 403 MEETUP_DISABLED unless the flag is on or the caller is a beta user. */
    public void requireEnabled(UUID userId) {
        if (!isEnabled(userId)) {
            throw new BusinessException(ErrorConstant.MEETUP_DISABLED, "Meetups are not available yet");
        }
    }

    /**
     * The caller's age, after checking they declared a birth year (409
     * MEETUP_BIRTH_YEAR_REQUIRED) and are an adult (403 MEETUP_UNDERAGE).
     */
    public int requireAdult(UUID userId, Instant now) {
        Integer birthYear = repository.findBirthYear(userId).orElse(null);
        if (birthYear == null) {
            throw new BusinessException(ErrorConstant.MEETUP_BIRTH_YEAR_REQUIRED,
                    "Add your birth year before hosting or joining meetups");
        }
        int age = MeetupSchedule.age(birthYear, now);
        if (age < MIN_AGE) {
            throw new BusinessException(ErrorConstant.MEETUP_UNDERAGE, "Meetups are for adults only");
        }
        return age;
    }
}
