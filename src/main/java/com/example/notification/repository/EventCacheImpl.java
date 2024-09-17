package com.example.notification.repository;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class EventCacheImpl implements EventCache {

    private final Map<String, Object> eventCache = new ConcurrentHashMap<>();

    @Override
    public void save(String eventId, Object eventData) {
        eventCache.put(eventId, eventData);
    }

    @Override
    public Map<String, Object> findAllByUsernameAndLessThanTime(String username, Long time) {
        return eventCache.entrySet().stream()
                .filter(event -> event.getKey().startsWith(username)
                        && event.getKey().compareTo(username + "-" + time) < 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void deleteByEventId(String eventId) {
        eventCache.remove(eventId);
    }
}
