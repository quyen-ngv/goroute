package com.ds.goroute.dto.request;

import lombok.Data;

import java.util.UUID;

/** Pins a message for everyone in a thread; a null id clears the pin. */
@Data
public class PinMessageRequest {

    private UUID messageId;
}
