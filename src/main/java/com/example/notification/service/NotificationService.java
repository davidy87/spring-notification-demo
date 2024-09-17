package com.example.notification.service;

import com.example.notification.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationService {

    private static final Long TIMEOUT = Duration.ofSeconds(30).toMillis();

    private final SseEmitterRepository sseEmitterRepository;

    public SseEmitter subscribe(String username) {
        log.info("subscriber = {}", username);
        SseEmitter sseEmitter = sseEmitterRepository.save(username, new SseEmitter(TIMEOUT));

        // onCompletion은 완료 시, timout 시, error 발생 시 모두 실행
        sseEmitter.onCompletion(() -> {
            log.info("SSE completed: subscriber = {}", username);
            sseEmitterRepository.delete(username);
        });

        sseEmitter.onTimeout(() -> {
            log.info("SSE timed out: subscriber = {}", username);
            sseEmitterRepository.delete(username);
            sseEmitter.complete();
        });

        sseEmitter.onError((e) -> {
            log.info("SSE error: subscriber = {}", username);
            sseEmitterRepository.delete(username);
            sseEmitter.complete();
        });

        // 503 Error 방지를 위한 Dummy notification 전송
        notify(sseEmitter, "SSE Connection", "SSE connected. Connected user = " + username);

        return sseEmitter;
    }

    public void notify(SseEmitter sseEmitter, String eventName, Object data) {
        try {
            sseEmitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.info("Exception occurred while sending notification.");
            throw new RuntimeException(e);
        }
    }
}
