package com.example.notification.repository;

import java.util.Map;

public interface EventCache {

    void save(String eventId, Object eventData);

    Map<String, Object> findAllByUsernameAndLessThanTime(String username, Long time);

    void deleteByEventId(String eventId);
}
