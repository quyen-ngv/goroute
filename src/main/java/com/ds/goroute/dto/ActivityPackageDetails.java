package com.ds.goroute.dto;

import com.ds.goroute.type.ActivityItineraryStopKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * The "Package details" page of one package, stored as {@code activity_packages.details}.
 * Every field is optional; the app hides a block whose data is missing. Blocks that also
 * exist on the product (included items, cancellation policy, redemption instructions) stay
 * on their own columns and are not repeated here.
 */
@Data
public class ActivityPackageDetails {
    /** Area the package runs in, e.g. "Hanoi Old Quarter". */
    @Size(max = 200) private String areaName;
    @Min(1) private Integer durationMinutes;

    @Valid @Size(max = 50) private List<ItineraryStop> itinerary;
    /** Footnote under the itinerary, e.g. "The schedule might change with traffic and weather". */
    @Size(max = 1000) private String itineraryNote;

    @Valid private TransferInfo departure;
    @Valid private TransferInfo returnInfo;

    /** "Before you book" eligibility bullets. */
    @Size(max = 30) private List<@Size(max = 1000) String> eligibility;
    /** Titled bullet groups, e.g. "Vehicle & luggage info". */
    @Valid @Size(max = 20) private List<InfoSection> additionalInfo;

    /** Whether the guest may move the booking to another date/time. */
    private Boolean changeable;
    @Size(max = 2000) private String changePolicy;
    /** Free text override of the voucher validity, e.g. "Valid until your selected date". */
    @Size(max = 1000) private String usageValidity;
    @Size(max = 30) private List<@Size(max = 1000) String> howToUse;

    @Data
    public static class ItineraryStop {
        private ActivityItineraryStopKind kind = ActivityItineraryStopKind.STOP;
        @NotBlank @Size(max = 300) private String title;
        @Size(max = 5000) private String description;
        @Min(0) private Integer durationMinutes;
        /** Short activity label, e.g. "Guided tour", "Free time". */
        @Size(max = 100) private String label;
        @Size(max = 10) private List<@Size(max = 2000) String> images;
    }

    /** Pick-up or meet-up information for the departure or the return leg. */
    @Data
    public static class TransferInfo {
        /** e.g. "Time confirmed after the booking". */
        @Size(max = 300) private String timeNote;
        /** Place or area name, e.g. "Designated pick-up area", "Cafe Pho Co, 11 Hang Gai". */
        @Size(max = 300) private String title;
        @Size(max = 3000) private String description;
        /** Selectable times shown as "HH:mm". */
        @Size(max = 48) private List<@Size(max = 5) String> times;
        @Size(max = 10) private List<@Size(max = 1000) String> notes;
    }

    @Data
    public static class InfoSection {
        @NotBlank @Size(max = 200) private String title;
        @Size(max = 30) private List<@Size(max = 1000) String> items;
    }
}
