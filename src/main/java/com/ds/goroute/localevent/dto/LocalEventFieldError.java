package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventFieldErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Why one field was refused. {@code field} is the JSON path, e.g. {@code venue.latitude} or {@code sessions[2].startAt}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventFieldError {

    private String field;
    private LocalEventFieldErrorCode code;
    private String message;

    public static LocalEventFieldError of(String field, LocalEventFieldErrorCode code, String message) {
        return new LocalEventFieldError(field, code, message);
    }
}
