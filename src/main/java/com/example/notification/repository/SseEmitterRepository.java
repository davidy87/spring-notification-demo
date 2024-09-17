package com.example.notification.repository;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

public interface SseEmitterRepository {

    SseEmitter save(String username, SseEmitter sseEmitter);

    Optional<SseEmitter> findByUsername(String username);

    void delete(String username);
}
