package com.example.notification.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventIdUtils {

    public static String generateEventId(String username) {
        return username + "-" + System.currentTimeMillis();
    }

    public static String parseUsernameFromEventId(String eventId) {
        return eventId.split("-")[0];
    }
}
