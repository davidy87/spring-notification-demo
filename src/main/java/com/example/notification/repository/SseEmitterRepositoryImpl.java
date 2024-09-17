package com.example.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseEmitterRepositoryImpl implements SseEmitterRepository {

    // key: username, value: SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter save(String username, SseEmitter sseEmitter) {
        emitters.put(username, sseEmitter);
        return sseEmitter;
    }

    @Override
    public Optional<SseEmitter> findByUsername(String username) {
        return Optional.ofNullable(emitters.get(username));
    }

    @Override
    public void delete(String username) {
        emitters.remove(username);
    }
}
