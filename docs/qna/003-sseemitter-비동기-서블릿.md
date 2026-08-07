# Q003. `SseEmitter` 가 "비동기 서블릿이라 스레드를 안 잡는다"는 게 무슨 뜻인가?

**챕터**: ch05 · **날짜**: 2026-08-07

ch05 STEP 7 의 "④ 커넥션을 서버가 들고 있어야 합니다" 각주에서 나온 질문.

---

## 1. 전통 서블릿 모델 — 요청 1건 = 스레드 1개

```
소켓에 요청 도착
   ↓
톰캣이 워커 스레드 풀에서 스레드 하나를 꺼냄  (http-nio-8080-exec-3)
   ↓
그 스레드가 DispatcherServlet → 컨트롤러 → JSON 직렬화 → 응답 write
   ↓
응답 완료 → 스레드를 풀에 반납
```

**요청 1건이 워커 스레드 1개를, 응답이 끝날 때까지 점유합니다.**

| 설정 | Spring Boot 기본값 | 의미 |
|---|---:|---|
| `server.tomcat.threads.max` | 200 | 동시에 처리 가능한 요청 수 |
| `server.tomcat.accept-count` | 100 | 스레드가 다 찼을 때 대기 큐 |
| `server.tomcat.max-connections` | 8192 | NIO 커넥터가 들고 있을 수 있는 커넥션 수 |

200개가 다 물리면 201번째부터 큐에 쌓이고, 큐도 넘치면 거부입니다.
이때 막히는 건 채팅 API 만이 아니라 **그 톰캣이 서빙하는 전부**입니다.

---

## 2. SSE 를 동기로 짜면 죽는다

SSE 는 응답을 **안 끝내는** 게 본질입니다.

```kotlin
// ❌ 개념 설명용. 실제로 쓰지 말 것
@GetMapping("/sse/connect")
fun connect(response: HttpServletResponse) {
    response.contentType = "text/event-stream"
    val writer = response.writer

    while (true) {                      // ← 응답을 안 끝내고 눌러앉음
        val event = queue.take()        // 새 메시지 올 때까지 블로킹
        writer.write("data: $event\n\n")
        writer.flush()
    }
}
```

이 메서드는 리턴하지 않으므로, 워커 스레드도 영원히 반납되지 않습니다.

| 접속자 수 | 점유된 워커 스레드 | 남은 스레드 (200 기준) | 상태 |
|---:|---:|---:|---|
| 10명 | 10 | 190 | 정상 |
| 100명 | 100 | 100 | 정상 |
| 190명 | 190 | 10 | 위험 |
| **200명** | **200** | **0** | **서버 전체 정지** |
| 201명 | 200 | 0 | 큐 대기 → 타임아웃 |

**아무도 대화하지 않고 접속만 해 있어도** 스레드 200개가 `queue.take()` 에서 잠들어 있습니다.
이것이 **thread-per-connection 모델의 한계**입니다.

---

## 3. Servlet 3.0 비동기가 푸는 방식

`request.startAsync()` 의 의미는 단 하나입니다.

> **"이 요청의 응답은 아직 안 끝났다. 하지만 지금 이 스레드는 반납할 테니,
> 응답은 나중에 다른 스레드가 마저 쓰겠다."**

**"응답이 안 끝난 것"과 "스레드가 물려 있는 것"이 분리됩니다.** 이게 전부입니다.

Spring MVC 에서는 반환 타입만 바꾸면 프레임워크가 알아서 `startAsync()` 를 부릅니다.
(`SseEmitter`, `DeferredResult`, `Callable`, `StreamingResponseBody`)

```kotlin
// ✅ ch06에서 실제로 쓸 방식
@GetMapping("/sse/connect", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun connect(): SseEmitter {
    val emitter = SseEmitter(60 * 1000L * 60)   // 타임아웃 1시간
    sseEmitters.add(emitter)                     // 목록에 보관
    return emitter                               // ← 즉시 리턴! 스레드 반납
}
```

`return emitter` 하는 순간:

1. `ResponseBodyEmitterReturnValueHandler` 가 `startAsync()` 호출
2. 톰캣은 응답을 **닫지 않고** 소켓을 열어둠
3. **워커 스레드는 풀로 반납됨**
4. `emitter` 는 `sseEmitters` 목록이 참조하므로 GC 안 됨

---

## 4. 값 추적 — `send()` 는 누가 부르나

**`emitter.send()` 를 부르는 스레드는 "메시지를 POST 한 사람의 요청 스레드"** 입니다.

시나리오: 방에 100명 접속, A가 메시지를 보냄.

| 시점 | 일어나는 일 | 점유 중인 워커 스레드 | 살아있는 SseEmitter |
|---|---|---:|---:|
| T0 | 100명이 `/sse/connect` 호출 | 순간 최대 100 → **즉시 0** | 100 |
| T1 | 아무도 말 안 함 (10분) | **0** | 100 |
| T2 | A가 `POST /messages` | **1** (`exec-7`) | 100 |
| T2+1ms | `exec-7` 이 메시지를 리스트에 저장 | 1 | 100 |
| T2+2ms | `exec-7` 이 emitter 100개에 `send()` 반복 | **여전히 1** | 100 |
| T2+8ms | POST 응답 200 반환, `exec-7` 반납 | **0** | 100 |
| T3 | 브라우저 100개가 이벤트 받고 각자 증분 GET | 순간 최대 100 → 즉시 0 | 100 |

**T1 을 보라. 100명이 연결돼 있는데 점유 스레드가 0이다.**
2절의 잘못된 구현에서는 이 칸이 100이었습니다.

남는 것은 **소켓(FD) 100개 + `SseEmitter` 객체 100개 + 응답 버퍼**뿐입니다.
따라서 **SSE 의 실질 한계는 `threads.max`(200) 가 아니라 `max-connections`(8192)** 쪽입니다.

---

## 5. 폴링과도 비교 — 폴링은 스레드를 안 먹는다

흔한 오해: "폴링은 스레드를 많이 먹는다" → **아닙니다.**

폴링 요청 1건 처리 시간이 5ms 라면, 100명이 초당 1회 폴링할 때 평균 동시 점유는

```
100 요청/초 × 0.005초 = 0.5 스레드
```

| | 스레드 점유 | 요청 수 | 지연 |
|---|---|---|---|
| 폴링 | 거의 없음 (0.5) | **초당 100건 (문제)** | 0.5초 (문제) |
| 동기 SSE (❌) | **100개 영구 점유 (치명적)** | 0 | ~0 |
| `SseEmitter` (✅) | 거의 없음 | 메시지당 1건 | ~0 |

**`SseEmitter` 는 "폴링의 스레드 효율" + "SSE 의 지연·트래픽 이점" 을 둘 다 가져갑니다.**
Servlet 3.0 비동기가 없었다면 SSE 는 실무에서 못 쓸 기술이었습니다.

---

## 6. 함정 — 비동기가 만능은 아니다

T2+2ms 칸을 다시 보면, `exec-7` 한 스레드가 emitter 100개에 **순차 write** 합니다.

```kotlin
emitters.forEach { emitter ->
    emitter.send(...)     // ← 블로킹 write
}
```

모바일에서 지하철에 들어간 클라이언트 하나 때문에 TCP 송신 버퍼가 꽉 차면 그 `send()` 가 블로킹됩니다.

- A의 POST 응답이 늦어짐
- 뒤쪽 emitter 들도 늦게 받음
- 이런 POST 가 동시에 200건이면 **결국 워커 스레드 200개가 다 물림**

> 비동기 서블릿이 해결한 것은 **"연결만 하고 조용한 상태"의 스레드 점유**이지,
> **"쓰는 순간"의 블로킹**이 아닙니다.

실무 대응:

1. `send()` 를 별도 스레드풀/큐로 넘긴다 (`@Async`, 브로드캐스트 전용 executor)
2. `IOException` 나면 즉시 목록에서 제거한다 → **ch06 의 `emitter.onError { remove(it) }`**
3. 논블로킹 스택으로 간다 (WebFlux `Flux<ServerSentEvent>`)

우리는 학습 목적이라 **2번(죽은 커넥션 정리)만 ch06 에서 다룹니다.**
그게 `SseEmitters` 클래스가 하는 일의 전부입니다.

---

## 7. 직접 확인하는 법

```bash
# 워커 스레드 개수 세기 (exec-* 가 톰캣 워커)
jcmd <pid> Thread.print | grep -c "http-nio-8080-exec"
```

또는 IntelliJ **Profiler → Thread Dump**, VisualVM **Threads** 탭에서
`http-nio-8080-exec-*` 를 확인. ch06 이후 탭을 여러 개 열어도 대부분
`Parked` / `Waiting` 상태로 놀고 있는 것을 볼 수 있습니다.

---

## 한 줄 요약

**비동기 서블릿 = "응답을 열어둔 채 스레드만 반납하는 것".**
`SseEmitter` 를 리턴하면 Spring 이 `startAsync()` 를 걸어주고,
실제 `send()` 는 나중에 메시지를 POST 한 사람의 스레드가 대신 써준다.
