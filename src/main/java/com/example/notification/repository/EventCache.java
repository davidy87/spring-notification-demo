package com.example.notification.repository;

import java.util.Map;

public interface EventCache {

    void save(String eventId, Object eventData);

    Map<String, Object> findAllOmittedEventsByUsername(String username);

    void deleteByEventId(String eventId);
}
