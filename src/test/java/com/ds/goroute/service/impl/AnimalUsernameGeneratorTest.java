package com.ds.goroute.service.impl;

import com.ds.goroute.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnimalUsernameGeneratorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AnimalUsernameGenerator generator = new AnimalUsernameGenerator(users, new Random(42));

    @Test
    void freeFirstPickIsAdjectiveAnimalAndFourDigits() {
        when(users.existsByUsername(anyString())).thenReturn(false);

        String name = generator.generate();

        assertTrue(name.matches("[A-Z][a-z]+[A-Z][a-z]+\\d{4}"), name);
        verify(users, times(1)).existsByUsername(anyString());
    }

    @Test
    void takenFourDigitPicksWidenToSixDigits() {
        // Every four-digit name is taken, every six-digit one is free.
        when(users.existsByUsername(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).matches(".*\\D\\d{4}"));

        String name = generator.generate();

        assertTrue(name.matches("[A-Z][a-z]+[A-Z][a-z]+\\d{6}"), name);
        verify(users, times(6)).existsByUsername(anyString());
    }

    @Test
    void everyPickTakenFallsBackToUuidSuffix() {
        when(users.existsByUsername(anyString())).thenReturn(true);

        String name = generator.generate();

        assertTrue(name.matches("[A-Z][a-z]+[A-Z][a-z]+[0-9a-f]{10}"), name);
        verify(users, times(10)).existsByUsername(anyString());
    }

    @Test
    void namesFitTheUsernameColumn() {
        when(users.existsByUsername(anyString())).thenReturn(true);

        for (int i = 0; i < 200; i++) {
            String name = generator.generate();
            assertTrue(name.length() <= 50, name);
            assertEquals(name, name.replaceAll("[^A-Za-z0-9]", ""));
        }
    }
}
