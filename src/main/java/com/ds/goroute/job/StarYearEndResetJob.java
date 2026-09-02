package com.ds.goroute.job;

import com.ds.goroute.service.StarYearEndResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Halves every star balance at the turn of the year.
 *
 * <p>Runs in the first hour of January rather than at midnight on the 31st: the year being closed
 * is then unambiguous, and a check-in posted at 23:59 still earns into the year it happened in.
 *
 * <p>Fires every day of January rather than once on the 1st. A single yearly firing that happens
 * while the service is down does not come back, and nobody notices until somebody asks why their
 * balance never dropped; a whole month of attempts survives any plausible outage. Repetition is
 * free because each deduction is keyed on the year -- the 2nd of January finds the work done.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StarYearEndResetJob {

    private final StarYearEndResetService resetService;

    @Scheduled(cron = "${goroute.jobs.star-year-end-reset-cron:0 30 0 * 1 *}")
    public void resetBalances() {
        int yearToClose = LocalDate.now().getYear() - 1;
        try {
            resetService.resetForYear(yearToClose);
        } catch (RuntimeException exception) {
            // Tomorrow's run picks up whatever this one missed, under the same key.
            log.error("Year-end star reset for {} did not finish: {}",
                    yearToClose, exception.getMessage(), exception);
        }
    }
}
