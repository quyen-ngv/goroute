package com.ds.goroute.service.impl;

import com.ds.goroute.entity.User;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BetaAccessServiceImpl implements BetaAccessService {

    private final BetaUserStore store;
    private final UserRepository users;

    @Override
    public boolean isBetaUser(UUID userId) {
        if (userId == null) return false;
        Set<String> allowed = store.usernames();
        // Checked before the user lookup: the list is empty far more often than not, and
        // an empty list cannot contain anybody.
        if (allowed.isEmpty()) return false;
        return users.findById(userId)
                .map(User::getUsername)
                .filter(username -> !username.isBlank())
                .map(username -> allowed.contains(username.trim().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }
}
