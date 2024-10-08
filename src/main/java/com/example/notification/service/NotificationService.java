package com.example.notification.service;

import com.example.notification.repository.EventCache;
import com.example.notification.repository.SseEmitterRepository;
import com.example.notification.util.EventIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;

import static com.example.notification.service.EventType.EVENT;
import static com.example.notification.service.EventType.SUBSCRIPTION;

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
            sseEmitter.complete();
        });

        sseEmitter.onError((e) -> {
            log.info("SSE error: subscriber = {}", username);
            log.info("error ", e);
            sseEmitter.complete();
        });

        if (StringUtils.hasText(lastEventId)) {
            notifyOmittedEvents(sseEmitter, username);  // 알림 전송이 누락된 이벤트들이 있을 경우, 다시 전송
        } else {
            notifyDummy(sseEmitter, username);  // 503 Error 방지를 위한 Dummy notification 전송
        }

        return sseEmitter;
    }

    /**
     * 이벤트가 발생하는 곳에서 알림을 전송하기 위해 호출하는 메서드
     */
    public void notifyEvent(String username, Object data) {
        SseEmitter sseEmitter = sseEmitterRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("SSE connection for user [" + username + "] is not established."));

        String eventId = EventIdUtils.generateEventId(username);
        eventCache.save(eventId, data); // 알림 전송 누락을 대비한 이벤트 저장
        notify(sseEmitter, eventId, EVENT.getEventName(), data);
        sseEmitter.complete();
    }

    /**
     * 알림 전송이 누락된 이벤트들을 조회한 후, 재전송하기 위해 호출하는 메서드
     */
    private void notifyOmittedEvents(SseEmitter sseEmitter, String username) {
        eventCache.findAllOmittedEventsByUsername(username)
                .forEach((eventId, data) -> {
                    notify(sseEmitter, eventId, EVENT.getEventName(), data);
                    eventCache.deleteByEventId(eventId);
                });
    }

    /**
     * 첫 SSE 연결 후, 더미 데이터를 보내기 위해 호출하는 메서드
     */
    private void notifyDummy(SseEmitter sseEmitter, String username) {
        notify(sseEmitter, "", SUBSCRIPTION.getEventName(), "SSE connected. Connected user = " + username);
    }

    private void notify(SseEmitter sseEmitter, String eventId, String eventName, Object data) {
        try {
            sseEmitter.send(SseEmitter.event()
                    .id(eventId)
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.info("Exception occurred while sending notification.");
            String username = EventIdUtils.parseUsernameFromEventId(eventId);
            sseEmitterRepository.deleteByUsername(username);
            throw new RuntimeException(e);
        }
    }
}
