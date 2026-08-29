package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateGuideBookingRequest {

    @NotNull(message = "A service is required")
    private UUID serviceId;

    @NotNull(message = "A date is required")
    @Future(message = "The date must be in the future")
    private LocalDate bookingDate;

    @NotNull @Min(1)
    private Integer guestCount;

    @Size(max = 2000)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_SERVICE, visibility = ModerationVisibility.DIRECT)
    private String travelerNote;

    /** Makes a retried request return the booking it already created. */
    @NotBlank(message = "An idempotency key is required")
    @Size(max = 120)
    private String idempotencyKey;
}
