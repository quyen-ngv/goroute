package com.ds.goroute.service.notification;

import lombok.Builder;

@Builder
public record NotificationMessage(String title, String body) {
}
