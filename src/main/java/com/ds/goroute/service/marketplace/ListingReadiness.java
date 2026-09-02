package com.ds.goroute.service.marketplace;

import java.util.List;

/**
 * Go-live checklist for a listing (hotel or activity product), modelled on Booking.com's
 * "open/bookable" check: a listing may only be switched to ENABLED when every <em>required</em>
 * check passes; the weighted score doubles as a content-completeness meter for the console.
 *
 * <p>Pure: the services collect the facts, this class only scores them.
 */
public final class ListingReadiness {
    private ListingReadiness() {}

    public record Check(String code, String label, boolean passed, boolean required, int weight, String detail) {}

    public record Result(boolean ready, int score, List<Check> checks) {
        public List<String> failingRequiredCodes() {
            return checks.stream().filter(c -> c.required() && !c.passed()).map(Check::code).toList();
        }
    }

    public static Result score(List<Check> checks) {
        int total = checks.stream().mapToInt(Check::weight).sum();
        int passed = checks.stream().filter(Check::passed).mapToInt(Check::weight).sum();
        boolean ready = checks.stream().filter(Check::required).allMatch(Check::passed);
        int score = total == 0 ? 0 : (int) Math.round(passed * 100.0 / total);
        return new Result(ready, score, List.copyOf(checks));
    }

    public static Check of(String code, String label, boolean passed, boolean required, int weight, String detail) {
        return new Check(code, label, passed, required, weight, detail);
    }
}
