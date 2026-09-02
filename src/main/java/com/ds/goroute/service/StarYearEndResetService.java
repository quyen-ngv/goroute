package com.ds.goroute.service;

import com.ds.goroute.mapper.StarMapper;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The turn of the year takes half of every balance (REWARD-01).
 *
 * <p>A point system with no ceiling stops meaning anything: a balance built over three years is
 * not worth what a balance built this year is worth, and the rewards catalogue has to be priced
 * for whoever has the most. Halving once a year keeps the currency comparable between people who
 * started at different times, without ever taking somebody's whole balance away.
 *
 * <p>It is a spend, not an edit. The wallet is moved by an ordinary ledger entry keyed on the
 * year, so the deduction can be read back, reconciled and explained -- and so running the job
 * twice, or restarting it halfway, cannot take half twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StarYearEndResetService {

    /** Wallets per keyset page. Small enough that a failure re-does very little work. */
    private static final int PAGE_SIZE = 200;

    private final StarMapper starMapper;
    private final StarService starService;
    private final BusinessConfigService config;

    /**
     * Halves every balance for the given year, once.
     *
     * @param year the year being closed, which is what the reference key is built from -- so the
     *             same year can be processed repeatedly with no further effect
     * @return how many wallets this call actually reduced
     */
    public int resetForYear(int year) {
        if (!config.getBoolean(BusinessConfigKey.POINTS_YEAR_END_RESET_ENABLED)) {
            log.info("Year-end star reset for {} skipped: switched off in configuration", year);
            return 0;
        }
        int retainPercent = config.getInt(BusinessConfigKey.POINTS_YEAR_END_RETAIN_PERCENT);
        if (retainPercent >= 100) {
            log.info("Year-end star reset for {} skipped: wallets keep {}%", year, retainPercent);
            return 0;
        }

        int reduced = 0;
        UUID cursor = null;
        while (true) {
            List<UUID> page = starMapper.findWalletsWithBalanceAfter(cursor, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (UUID userId : page) {
                // One wallet per transaction. A wallet that fails -- a balance moved by a
                // concurrent spend, a row that vanished -- must not roll back the hundreds
                // already done, and will be picked up by the next run under the same key.
                try {
                    if (resetWallet(userId, year, retainPercent)) {
                        reduced++;
                    }
                } catch (RuntimeException exception) {
                    log.error("Year-end star reset failed for wallet {}: {}",
                            userId, exception.getMessage(), exception);
                }
            }
            cursor = page.get(page.size() - 1);
        }
        log.info("Year-end star reset for {}: {} wallets reduced to {}%", year, reduced, retainPercent);
        return reduced;
    }

    /**
     * Takes this wallet's share for the year.
     *
     * <p>Deliberately not {@code @Transactional}: it is called from {@link #resetForYear} in the
     * same bean, where the annotation would be bypassed anyway and would only read as a promise
     * the code does not keep. It does not need one -- the ledger entry that moves the balance is
     * written atomically inside {@code StarService.spend}, and the reference key means a partial
     * run is simply resumed on the next pass.
     *
     * @return true when this call is what took the points, false when the wallet was already
     *         reset for this year or had nothing to take
     */
    public boolean resetWallet(UUID userId, int year, int retainPercent) {
        String reference = "year_end_reset:" + year + ":" + userId;
        // Asked before the balance is read, because spend() answers a replay by handing back the
        // original entry rather than by failing -- correct, but indistinguishable from having
        // just taken the points if the caller only looks at the return value.
        if (starService.findByReference(reference).isPresent()) {
            return false;
        }
        int balance = starService.getBalance(userId);
        if (balance <= 0) {
            return false;
        }
        // Rounded up, so the rounding always favours the user: a balance of 7 keeps 4.
        int retained = (int) Math.ceil(balance * retainPercent / 100.0);
        int deduction = balance - retained;
        if (deduction <= 0) {
            return false;
        }
        starService.spend(userId, deduction, "YEAR_END_RESET", reference,
                "End of " + year + ": balance reduced to " + retainPercent + "%");
        return true;
    }
}
