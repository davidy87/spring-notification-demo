# Spring Notification Demo
Spring에서 Server-Sent Event 통신을 사용한 실시간 알림 기능 구현

## NotificationController
```java
@RequestMapping("/sse")
@RequiredArgsConstructor
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/subscribe", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader(value = "Last-Event-Id", required = false) String lastEventId) {
        return notificationService.subscribe("user1", lastEventId);
    }
}
```
* 클라이언트가 SSE 연결을 위해 `/subscribe`로 요청하면, 서버는 응답의 미디어 타입을 
`text/event-stream`으로 전달하며 SSE 연결이 시작된다.

* Spring framework 4.2부터 지원하는 `SseEmitter`를 사용하여 SSE 구독 요청에 대한 응답을 진행할 수 있다.

* `Last-Event-Id`: SSE 구독 요청 시 전달할 수 있는 헤더로, 클라이언트가 가장 마지막으로 알림을 받은 이벤트의 
  id이다. 
  * 클라이언트가 보낸 요청에 이 헤더가 포함되어 있는 경우, 이전 SSE 연결이 중간에 종료되어 이벤트 알림이 
    누락되었을 수 있다는 뜻이므로, 서버는 이 값을 바탕으로 알림이 누락된 이벤트를 모두 찾아 재전송해야 한다.

<br>

## NotificationService
### `subscribe()`
SSE 연결 구독을 위한 메서드
```java
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
```
* 클라이언트로부터 SSE 구독 요청이 들어오면, controller는 `NotificationService.subscirbe()`를 호출한다. 

* `subscribe()`는 먼저 `SseEmitter` 객체를 생성 후 `SseEmitterRepository`에 저장한다. 이후 해당 `SseEmitter`
가 알림 전송 완료 시, timeout 발생 시, 혹은 error 발생 시에 어떤 작업을 수행할 지 설정해준다.

* 첫 SSE 연결 이후 아무 데이터도 전송하지 않는다면 `503 Service Unavaiable` Error를 반환하기 때문에 더미 데이터를 전송한다.

* 만약, SSE 구독 요청에 `Last-Event-Id` 헤더가 포함되었다면, 중간에 연결 문제가 생겨 알림 전송이 누락되었다는 것을 의미하므로
알림 전송이 누락된 이벤트들을 확인하여 다시 전송한다.
  * 이 경우에는, 더미 데이터를 전송할 필요가 없다.


### `notifyEvent()`
이벤트가 발생하는 곳에서 알림을 보내기 위해 호출하는 메서드
```java
public void notifyEvent(String username, Object data) {
    SseEmitter sseEmitter = sseEmitterRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("SSE connection for user [" + username + "] is not established."));

    String eventId = EventIdUtils.generateEventId(username);
    eventCache.save(eventId, data); // 알림 전송 누락을 대비한 이벤트 저장
    notify(sseEmitter, eventId, EVENT.getEventName(), data);
    sseEmitter.complete();
}

```
1. `SseEmitterRepository`에서 `username`으로 `SseEmitter` 객체를 조회한다.
2. 이벤트 알림 전송이 누락될 것을 대비해 `EventCache`에 알림을 보낼 데이터를 저장한다.
3. `notify()`를 호출하여 알림을 전송한다.
4. `SseEmitter.complete()`을 호출하여 알림 전송을 완료한다.

### 전체 코드
```java
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
```

<br>

## Repository
### SseEmitterRepository
```java
public interface SseEmitterRepository {

    SseEmitter save(String username, SseEmitter sseEmitter);

    Optional<SseEmitter> findByUsername(String username);

    void deleteByUsername(String username);
}

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
    public void deleteByUsername(String username) {
        emitters.remove(username);
    }
}
```
* `SseEmitter`를 저장하고 관리하는 repository이다.

* 동시성 문제에 대비해 구현체로 `ConcurrentHashMap`을 사용하며, key는 username(문자열)로, value는 `SseEmitter` 
  객체로 지정하여 저장한다.

### EventCache
```java
public interface EventCache {

    void save(String eventId, Object eventData);

    Map<String, Object> findAllOmittedEventsByUsername(String username);

    void deleteByEventId(String eventId);
}

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
```
* 이벤트 데이터를 임시로 저장하고 관리하는 클래스이다.

* 동시성 문제에 대비해 구현체로 `ConcurrentHashMap`을 사용하며, key는 eventId(문자열)로, value는 알림을 보낼
  데이터로 지정하여 저장한다.

<br>

## Utility

### EventIdUtils
```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventIdUtils {

    public static String generateEventId(String username) {
        return username + "-" + System.currentTimeMillis();
    }

    public static String parseUsernameFromEventId(String eventId) {
        return eventId.split("-")[0];
    }
}
```
* 이벤트 id 유틸리티 클래스이다.

* 이벤트 id는 `{username}-{System.currentTimeMillis()}` 형태로 만들어진다.
  * 알림이 누락된 이벤트를 찾기 수월하도록 id에 시간을 milliseconds로 만들어 포함하였다.