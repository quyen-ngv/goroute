package com.ds.goroute.service;

import java.util.UUID;

/**
 * Whether an account is on the beta list.
 *
 * <p>Beta testers see features that are switched off for everybody else. A feature flag
 * answers "is this open to the public yet"; this answers "is this person allowed in
 * early", and a feature gate is the two questions together:
 *
 * <pre>{@code
 * boolean available = businessConfig.getBoolean(SOME_FEATURE_ENABLED)
 *         || betaAccess.isBetaUser(actorUserId);
 * }</pre>
 *
 * <p>The list is the {@code USER}/{@code BETA_USER} row of the {@code config} table — the
 * same row the app has read through {@code /v1/api/public/configs} since the marketplace
 * beta. Keeping one list rather than a flag per feature is deliberate: the cohort is the
 * same handful of people every time, and a second list is a second thing to forget to
 * update.
 */
public interface BetaAccessService {

    /** False for an unknown account, an account with no username, or an empty list. */
    boolean isBetaUser(UUID userId);
}
