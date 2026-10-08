package com.ds.goroute.service.impl;

import com.ds.goroute.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Names an account whose provider gave us neither a usable email nor a display name
 * (Sign in with Apple with "Hide My Email" and no name). The result, e.g.
 * {@code BravePanda4821}, is used as both username and full name, so it must be unique
 * and safe as a username: ASCII letters and digits only.
 *
 * <p>Collisions: about 9.5 million combinations, so a random pick is almost always free.
 * Each pick is checked against every row, soft-deleted ones included, because the
 * {@code users.username} UNIQUE constraint covers them too. After a few taken picks the
 * number widens to six digits, and the last resort appends a UUID fragment, which never
 * collides in practice.
 */
@Component
public class AnimalUsernameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "Brave", "Happy", "Lucky", "Sunny", "Swift", "Clever", "Gentle", "Jolly",
            "Mighty", "Calm", "Curious", "Bold", "Cheerful", "Cozy", "Breezy", "Bright",
            "Witty", "Merry", "Nimble", "Daring", "Fluffy", "Sleepy", "Wild", "Golden",
            "Silver", "Misty", "Rusty", "Snowy", "Zesty", "Dreamy");

    private static final List<String> ANIMALS = List.of(
            "Panda", "Otter", "Fox", "Tiger", "Koala", "Penguin", "Dolphin", "Falcon",
            "Owl", "Rabbit", "Turtle", "Squirrel", "Hedgehog", "Lynx", "Wolf", "Bear",
            "Elephant", "Giraffe", "Zebra", "Kangaroo", "Raccoon", "Beaver", "Hamster", "Parrot",
            "Peacock", "Swan", "Deer", "Buffalo", "Gecko", "Crane", "Heron", "Seal",
            "Whale", "Octopus", "Jaguar");

    /** Four-digit picks before widening; each costs one indexed lookup. */
    private static final int SHORT_ATTEMPTS = 5;
    private static final int WIDE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RandomGenerator random;

    @Autowired
    public AnimalUsernameGenerator(UserRepository userRepository) {
        this(userRepository, new SecureRandom());
    }

    AnimalUsernameGenerator(UserRepository userRepository, RandomGenerator random) {
        this.userRepository = userRepository;
        this.random = random;
    }

    /** @return a name no account, live or soft-deleted, holds yet. */
    public String generate() {
        for (int attempt = 0; attempt < SHORT_ATTEMPTS; attempt++) {
            String candidate = randomBase() + random.nextInt(1000, 10000);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        for (int attempt = 0; attempt < WIDE_ATTEMPTS; attempt++) {
            String candidate = randomBase() + random.nextInt(100000, 1000000);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        return randomBase() + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String randomBase() {
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
                + ANIMALS.get(random.nextInt(ANIMALS.size()));
    }
}
