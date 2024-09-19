package com.example.notification.repository;

import com.example.notification.util.EventIdUtils;
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
    public Map<String, Object> findAllOmittedEventsByUsername(String username) {
        return eventCache.entrySet().stream()
                .filter(event -> event.getKey().startsWith(username))
                .filter(event -> event.getKey().compareTo(EventIdUtils.generateEventId(username)) < 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void deleteByEventId(String eventId) {
        eventCache.remove(eventId);
    }
}
