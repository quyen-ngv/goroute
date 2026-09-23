package com.ds.goroute.service.impl;

import com.ds.goroute.entity.AppConfig;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who counts as a beta tester.
 *
 * <p>The list is typed by hand into an admin form, so the parsing has to survive the
 * ways a person types a list. Getting this wrong either locks a tester out of the thing
 * they were recruited to test, or lets everybody in early.
 */
@DisplayName("BetaAccessService")
class BetaAccessServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private AppConfigRepository configs;
    private UserRepository users;
    private BetaAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        configs = mock(AppConfigRepository.class);
        users = mock(UserRepository.class);
        service = new BetaAccessServiceImpl(new BetaUserStore(configs, new ObjectMapper()), users);
    }

    private void listContains(String raw) {
        AppConfig config = new AppConfig();
        config.setValue(raw);
        when(configs.findActiveByLabelAndKey("USER", "BETA_USER")).thenReturn(Optional.of(config));
    }

    private void signedInAs(String username) {
        User user = new User();
        user.setUsername(username);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("reading the list")
    class ReadingTheList {

        @Test
        @DisplayName("accepts a comma-separated list")
        void commaSeparated() {
            listContains("ann, bob ,carol");
            signedInAs("bob");

            assertThat(service.isBetaUser(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("accepts a JSON array")
        void jsonArray() {
            listContains("[\"ann\", \"bob\"]");
            signedInAs("ann");

            assertThat(service.isBetaUser(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("falls back to the delimiter parser when the array is malformed")
        void malformedJson() {
            // A missing bracket should not quietly empty the list and lock every tester out.
            listContains("[\"ann\", \"bob\"");
            signedInAs("bob");

            assertThat(service.isBetaUser(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("ignores how either side was capitalised")
        void caseInsensitive() {
            listContains("Ann,BOB");
            signedInAs("bOb");

            assertThat(service.isBetaUser(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("says no to somebody not on it")
        void notOnTheList() {
            listContains("ann,bob");
            signedInAs("carol");

            assertThat(service.isBetaUser(USER_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("when there is nothing to check against")
    class NothingToCheck {

        @Test
        @DisplayName("says no, and does not look the account up")
        void noConfigRow() {
            when(configs.findActiveByLabelAndKey(any(), any())).thenReturn(Optional.empty());

            assertThat(service.isBetaUser(USER_ID)).isFalse();
            // An empty list cannot contain anybody, so the query is pure waste.
            verify(users, never()).findById(any());
        }

        @Test
        @DisplayName("says no for a blank list")
        void blankList() {
            listContains("   ");

            assertThat(service.isBetaUser(USER_ID)).isFalse();
            verify(users, never()).findById(any());
        }

        @Test
        @DisplayName("says no for a signed-out caller")
        void noUser() {
            listContains("ann");

            assertThat(service.isBetaUser(null)).isFalse();
        }

        @Test
        @DisplayName("says no when the account has no username")
        void userWithoutUsername() {
            listContains("ann");
            signedInAs(null);

            assertThat(service.isBetaUser(USER_ID)).isFalse();
        }
    }
}
