package com.example.notification.service;

import com.example.notification.repository.EventCache;
import com.example.notification.repository.SseEmitterRepository;
import io.micrometer.common.util.StringUtils;
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

    private final EventCache eventCache;

    public SseEmitter subscribe(String username, String lastEventId) {
        log.info("subscriber = {}", username);
        SseEmitter sseEmitter = sseEmitterRepository.save(username, new SseEmitter(TIMEOUT));

        // onCompletion은 완료 시, timeout 시, error 발생 시 모두 실행
        sseEmitter.onCompletion(() -> {
            log.info("SSE completed: subscriber = {}", username);
            sseEmitterRepository.deleteByUsername(username);
        });

        sseEmitter.onTimeout(() -> {
            log.info("SSE timed out: subscriber = {}", username);
            sseEmitterRepository.deleteByUsername(username);
            sseEmitter.complete();
        });

        sseEmitter.onError((e) -> {
            log.info("SSE error: subscriber = {}", username);
            sseEmitterRepository.deleteByUsername(username);
            sseEmitter.complete();
        });

        notifyDummy(sseEmitter, username);  // 503 Error 방지를 위한 Dummy notification 전송

        if (!StringUtils.isEmpty(lastEventId)) {
            notifyOmittedEvents(sseEmitter, username);  // 알림 전송이 누락된 이벤트들이 있을 경우, 다시 전송
        }

        return sseEmitter;
    }

    /**
     * 이벤트가 발생하는 곳에서 알림을 전송하기 위해 호출하는 메서드
     */
    public void notifyEvent(String username, Object data) {
        SseEmitter sseEmitter = sseEmitterRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("SSE connection is not established."));

        String eventId = username + "-" + System.currentTimeMillis();
        eventCache.save(eventId, data); // 알림 전송 누락을 대비한 이벤트 저장
        notify(sseEmitter, eventId, "event", data);
    }

    /**
     * 알림 전송이 누락된 이벤트들을 조회한 후, 재전송하기 위해 호출하는 메서드
     */
    private void notifyOmittedEvents(SseEmitter sseEmitter, String username) {
        eventCache.findAllByUsernameAndLessThanTime(username, System.currentTimeMillis())
                .forEach((eventId, data) -> {
                    notify(sseEmitter, eventId, "event", data);
                    eventCache.deleteByEventId(eventId);
                });
    }

    /**
     * 첫 SSE 연결 후, 더미 데이터를 보내기 위해 호출하는 메서드
     */
    private void notifyDummy(SseEmitter sseEmitter, String username) {
        notify(sseEmitter, "", "SSE Connection", "SSE connected. Connected user = " + username);
    }

    private void notify(SseEmitter sseEmitter, String eventId, String eventName, Object data) {
        try {
            sseEmitter.send(SseEmitter.event()
                    .id(eventId)
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.info("Exception occurred while sending notification.");
            String username = eventId.split("-")[0];
            sseEmitterRepository.deleteByUsername(username);
            throw new RuntimeException(e);
        }
    }
}
