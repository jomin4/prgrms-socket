# 원본 자료 정리 — 앞으로 나올 코드

> 강의 원본(slog.gg 글 + CodePen 펜 9개 + GitHub 태그 5개)을 분석해서 옮겨둔 것입니다.
> **다시 크롤링하지 말고 여기를 보세요.** (CodePen 은 브라우저 JS 로 `init-data` 를
> 파싱해야만 코드가 나와서 재수집 비용이 큽니다.)
>
> 표기: **[원본]** = 강의 코드 그대로 · **[적용]** = 이 프로젝트에 맞게 수정 ·
> **[재구성]** = 강의 설명을 근거로 복원(원문 대조 안 됨)

---

## 강의 12강 ↔ 우리 챕터 매핑

| 강 | 강의 내용 | 우리 챕터 | 원본 태그 |
|---|---|---|---|
| 1강 | 스프링부트 프로젝트 세팅 | ch01~ch04 | `0001` |
| 2강 | 폴링 vs SSE 방식 | ch05 | — |
| 3강 | 메시지 그리는 방식 비교 | ch05 | — |
| 4강 | 미션1 — 폴링 없애고 SSE | ch06~ch07 | — |
| 5강 | 미션1 정답 | ch06~ch07 | `0005` |
| 6강 | 미션2 — 쓰로틀링 | ch08 | — |
| 7강 | 미션2 정답 | ch08 | — |
| 8강 | 미션3 — 입장/퇴장 메시지 | ch09 | — |
| 9강 | 미션3 정답 | ch09 | `0009` |
| 10강 | 로컬 HTTPS 적용 | ch10 | `0010` |
| 11강 | 미션4 — SSE 대신 STOMP | ch11 | — |
| 12강 | 미션4 정답 | ch11 | `0012` |

원본 레포: https://github.com/jhs512/p-14135-1
CodePen: `dPXwpJv`(폴링 시작) → `vEKvyEj`(SSE 1단계) → `ZYOVBbW`(SSE 2단계) →
`dPXwNdb`(쓰로틀링) → `dPXwNao` → `emzbvNo`(방 이동+입퇴장) → `MYeZmzm`(https) →
`yyJGbmX`(stomp 연결) → `WbxLOrR`(stomp 최종)
주소 형식: `https://codepen.io/jangka44/pen/<ID>`

---

## ch06 — 백엔드 SSE

### `global/sse/SseEmitters.kt` **[원본]**

```kotlin
package com.back.global.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEmitters {
    private val emittersByKey = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun connect(key: String, timeout: Long = 60 * 60 * 1000L): SseEmitter {
        val emitter = SseEmitter(timeout)
        val emitters = emittersByKey.computeIfAbsent(key) { CopyOnWriteArrayList() }
        emitters.add(emitter)

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }

        return emitter
    }

    fun send(key: String, eventName: String, data: Any) {
        val emitters = emittersByKey[key] ?: return

        emitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventName)
                        .data(data)
                )
            } catch (_: Exception) {
                emitters.remove(emitter)
            }
        }
    }
}
```

**설명 포인트**
- `ConcurrentHashMap` + `CopyOnWriteArrayList` — ch02 의 `mutableListOf` 와 대비해서 설명할 것.
  여기서는 **여러 스레드가 진짜로 동시에** 접근한다 (요청 스레드가 `send`, 다른 요청 스레드가 `connect`,
  타임아웃 스레드가 `remove`). `CopyOnWriteArrayList` 는 순회 중 삭제해도 `ConcurrentModificationException` 이 안 남
  → `send` 안에서 `emitters.remove(emitter)` 를 하는 코드가 성립하는 이유.
- `key` 는 `"chat__room__2"` 형태. 방 단위로 구독자를 묶는다.
- 3중 정리(`onCompletion`/`onTimeout`/`onError`) — 안 하면 죽은 커넥션이 쌓여 메모리 누수.
- 타임아웃 1시간. 브라우저 `EventSource` 는 끊기면 **자동 재연결**한다는 점도 설명.

### `domain/sse/controller/SseController.kt` **[원본]**

```kotlin
package com.back.domain.sse.controller

import com.back.global.sse.SseEmitters
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/sse")
@CrossOrigin(origins = ["https://cdpn.io"])
class SseController(
    private val sseEmitters: SseEmitters
) {
    @GetMapping("/connect/{key}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun connect(@PathVariable key: String): SseEmitter {
        return sseEmitters.connect(key)
    }
}
```

**설명 포인트**
- `produces = TEXT_EVENT_STREAM_VALUE` → `Content-Type: text/event-stream`. 이게 SSE 의 전부.
- 응답을 **끝내지 않고 열어둔다**. 일반 REST 와 근본적으로 다른 점.
- 생성자 주입 — ch02/ch03 컨트롤러는 의존성이 없었으니 여기서 처음 설명.

### `ApiV1ChatMessageController` 변경 **[원본]**

```kotlin
class ApiV1ChatMessageController(
    private val sseEmitters: SseEmitters      // ← 추가
) {
```
`write()` 의 `chatMessages.add(chatMessage)` 바로 뒤에 한 줄:
```kotlin
        sseEmitters.send("chat__room__$chatRoomId", "chat__messageCreated", chatMessage)
```

> **"폴링을 없애는 데 필요한 백엔드 변경은 이 한 줄이 전부"** 라는 점을 강조할 것.

---

## ch07 — 프론트 SSE

### ❌ 1단계: 잘못된 방식 **[재구성]**

SSE 로 온 메시지 **데이터를 직접** 화면에 넣는 방식. 강의가 일부러 먼저 보여주는 버전.

```jsx
const eventSource = new EventSource(`/sse/connect/chat__room__${chatRoomId}`);

eventSource.addEventListener("chat__messageCreated", (e) => {
  const newMessage = JSON.parse(e.data);
  setChatMessages((prev) => [newMessage, ...prev]);   // ← 문제
});
```

**무엇이 깨지는지 값으로 보여줄 것**
- 최초 로딩(`loadMoreChatMessages`)과 SSE 수신이 **경쟁**한다 → 순서 뒤바뀜/누락
- `lastChatMessageId.current` 가 **안 올라간다** → 나중에 폴백 로딩하면 중복
- SSE 재연결 중 발행된 메시지는 **영영 못 받는다** (커서로 메꿀 수 없음)

### ✅ 2단계: 올바른 방식 **[원본 — pen `ZYOVBbW`]**

SSE 를 **"새 게 있다" 신호로만** 쓰고, 데이터는 기존 커서 API 로 가져온다.

```jsx
useEffect(() => {
  // 채팅방 정보 가져오기
  fetch(`/api/v1/chat/rooms/${chatRoomId}`)
    .then((response) => response.json())
    .then(setChatRoom);

  loadMoreChatMessages();

  // SSE 연결
  const eventSource = new EventSource(
    `/sse/connect/chat__room__${chatRoomId}`
  );

  eventSource.addEventListener("chat__messageCreated", (e) => {
    loadMoreChatMessages();
  });

  return () => eventSource.close();
}, [chatRoomId]);
```

**핵심** — `setInterval` 이 `EventSource` 로 바뀌었을 뿐,
`loadMoreChatMessages` 는 **한 글자도 안 바뀐다.** ch03 의 커서 설계가 여기서 값을 한다.
`e.data` 를 아예 쓰지 않는 것이 포인트.

---

## ch08 — 쓰로틀링 **[원본 — pen `dPXwNdb` / `WbxLOrR`]**

import 추가:
```jsx
import React, { useState, useEffect, useRef, useCallback } from "https://esm.sh/react@19";
import { throttle } from "https://esm.sh/lodash-es";
```

기존 함수 이름 앞에 `_` 를 붙이고 throttle 로 감싼다:
```jsx
const _loadMoreChatMessages = async () => {
  const response = await fetch(
    `/api/v1/chat/rooms/${chatRoomId}/messages?afterChatMessageId=${lastChatMessageId.current}`
  );

  const data = await response.json();

  data.reverse();
  lastChatMessageId.current = data[0].id;
  setChatMessages((prevMessages) => [...data, ...prevMessages]);
};

const loadMoreChatMessages = useCallback(
  throttle(() => {
    _loadMoreChatMessages();
  }, 300),
  [chatRoomId]
);
```

**설명 포인트**
- **쓰로틀링**: "아무리 많이 불러도 300ms 마다 한 번만 실행"
  **디바운싱**: "연달아 오면 무시하고, 멈춘 뒤 마지막 한 번만 실행"
  → 채팅에는 쓰로틀링이 맞다 (디바운싱이면 계속 떠들 때 화면이 영영 안 갱신됨)
- `useCallback` 이 없으면 **리렌더마다 새 throttle 함수가 생겨** 쓰로틀 상태가 초기화된다 → 무의미해짐
- `async/await` 로 바뀐 것도 설명 (`.then` 체인과 같은 일, 읽기 쉬운 문법)
- ⚠️ `if (data.length > 0)` 가드가 **사라졌다.** SSE 신호가 올 때만 호출되니 빈 배열이 안 온다는 전제.
  이 전제가 깨지면 `data[0].id` 에서 터진다 — 함정으로 짚어줄 것.

---

## ch09 — 입장/퇴장 시스템 메시지

### 백엔드 **[원본]** — `ApiV1ChatMessageController` 에 추가

```kotlin
    @PostMapping("/entry")
    fun entry(@PathVariable chatRoomId: Int) {
        val systemMessage = ChatMessage(
            ++lastChatMessageId,
            LocalDateTime.now(),
            LocalDateTime.now(),
            chatRoomId,
            "system",
            "새로운 사용자가 입장했습니다."
        )

        sseEmitters.send("chat__room__$chatRoomId", "chat__systemMessageCreated", systemMessage)
    }

    @PostMapping("/exit")
    fun exit(@PathVariable chatRoomId: Int) {
        val systemMessage = ChatMessage(
            ++lastChatMessageId,
            LocalDateTime.now(),
            LocalDateTime.now(),
            chatRoomId,
            "system",
            "어떤 사용자가 퇴장했습니다."
        )

        sseEmitters.send("chat__room__$chatRoomId", "chat__systemMessageCreated", systemMessage)
    }
```

> ⚠️ **시스템 메시지는 `chatMessagesByRoomId` 에 저장하지 않는다.** 발행만 하고 버린다.
> 그래서 이건 **커서로 다시 못 가져온다** → 프론트가 `e.data` 를 **직접** 써야 하는
> 유일한 예외다. ch07 에서 "직접 쓰지 마라" 했던 것과 왜 다른지 반드시 대비해서 설명할 것.
> (id 는 소비하므로 일반 메시지 번호에 구멍이 생긴다는 점도 관찰거리)

### 프론트 **[원본 — pen `emzbvNo` 계열]**

```jsx
const [chatRoomId, setChatRoomId] = useState(2);   // 고정값 → 상태로

useEffect(() => {
  lastChatMessageId.current = 0;
  setChatMessages([]);

  fetch(`/api/v1/chat/rooms/${chatRoomId}`)
    .then((response) => response.json())
    .then(setChatRoom);

  const eventSource = new EventSource(`/sse/connect/chat__room__${chatRoomId}`);

  (async () => {
    await _loadMoreChatMessages();          // ① 기존 메시지 먼저

    eventSource.addEventListener("chat__messageCreated", () => {
      loadMoreChatMessages();
    });
    eventSource.addEventListener("chat__systemMessageCreated", (e) => {
      const newMessage = JSON.parse(e.data);
      setChatMessages((prev) => [newMessage, ...prev]);
    });

    fetch(`/api/v1/chat/rooms/${chatRoomId}/messages/entry`, { method: "POST" });  // ② 그다음 입장 알림
  })();

  return () => {
    eventSource.close();
    fetch(`/api/v1/chat/rooms/${chatRoomId}/messages/exit`, { method: "POST" });
  };
}, [chatRoomId]);
```

방 이동 버튼:
```jsx
<div className="flex gap-2">
  <button className="border p-2" onClick={() => setChatRoomId(1)}>1번 채팅방</button>
  <button className="border p-2" onClick={() => setChatRoomId(2)}>2번 채팅방</button>
  <button className="border p-2" onClick={() => setChatRoomId(3)}>3번 채팅방</button>
</div>
```

**설명 포인트**
- **순서가 핵심**: 기존 메시지 로딩이 끝난 뒤에 입장 알림을 보내야 한다.
  안 그러면 "입장했습니다" 가 옛날 메시지들보다 **위에** 떠버린다
  (강의 9강 1단계가 이걸 고치는 단계).
- `lastChatMessageId.current = 0` + `setChatMessages([])` — 방을 옮기면 커서와 목록을 리셋.
  안 하면 이전 방 메시지가 남고 커서가 이어져서 새 방 메시지를 못 받는다.
- cleanup 이 이제 두 가지 일(연결 해제 + 퇴장 알림)을 한다.

---

## ch10 — 로컬 HTTPS

인증서 생성 (프로젝트 루트에서):
```bash
keytool -genkey -alias sb-ssl -storetype PKCS12 -keyalg RSA -keysize 2048 -keystore keystore.p12 -validity 3650
```
- 비밀번호는 `123456`, 나머지 항목은 빈칸 엔터, 마지막 확인에 `y`
- 다시 만들려면 기존 파일 삭제 (`Remove-Item keystore.p12`)
- `keytool` 이 PATH 에 없으면 JDK 의 `bin` 으로 이동해서 실행
- 이 PC 는 `keytool` 이 PATH 에 있음 (Temurin JDK 25)
- **`keystore.p12` 는 `.gitignore` 됨. 커밋하지 말 것.**

`application.yaml` **[원본]**:
```yaml
server:
  ssl:
    enabled: true
    key-store-type: PKCS12
    key-store-password: 123456
    key-store: keystore.p12
```

**설명 포인트**
- 자가서명이라 브라우저가 경고 → "고급 → 계속" 으로 진행
- **프론트는 수정 불필요** (상대경로를 썼기 때문). 강의는 `http://` → `https://` 를
  전부 치환하는 단계가 따로 있었다는 걸 대비해서 알려줄 것.
- ch11 STOMP 를 위해 필요한 준비 (`wss://`)

---

## ch11 — WebSocket(STOMP)

### `build.gradle.kts` **[원본]**
```kotlin
    implementation("org.springframework.boot:spring-boot-starter-websocket")
```

### `global/stomp/StompSimpleBrokerConfig.kt` **[원본]**
```kotlin
package com.back.global.stomp

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class StompSimpleBrokerConfig : WebSocketMessageBrokerConfigurer {
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("https://cdpn.io")
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }
}
```

> **[적용]** 우리는 같은 오리진에서 서빙하므로 `setAllowedOrigins` 에
> `"https://localhost:8080"` 을 추가하거나 조정이 필요할 수 있음. 실행해보고 판단할 것.

### 컨트롤러 변경 **[원본]**
```kotlin
class ApiV1ChatMessageController(
    private val sseEmitters: SseEmitters,
    private val messagingTemplate: SimpMessagingTemplate      // ← 추가
) {
```
`write()` / `entry()` / `exit()` 의 `sseEmitters.send(...)` 아래에 각각 한 줄씩:
```kotlin
messagingTemplate.convertAndSend("/topic/chat/room/$chatRoomId/messageCreated", chatMessage)
messagingTemplate.convertAndSend("/topic/chat/room/$chatRoomId/systemMessageCreated", systemMessage)
```

> SSE 코드를 **지우지 않고 병행**한다. 둘을 비교하며 전환하기 위함.

### 프론트 **[원본 — pen `WbxLOrR`]**

HTML 에 라이브러리 추가:
```html
<script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
```

모듈 최상단 (컴포넌트 밖 — 연결은 앱 전체에서 하나):
```jsx
const socket = new SockJS("/ws");
const stompClient = Stomp.over(socket);

const stompConnected = new Promise((resolve) => {
  stompClient.connect({}, function (frame) {
    console.log("Connected: " + frame);
    resolve();
  });
});
```

`useEffect` 안:
```jsx
let sub1, sub2;

lastChatMessageId.current = 0;
setChatMessages([]);

fetch(`/api/v1/chat/rooms/${chatRoomId}`)
  .then((response) => response.json())
  .then(setChatRoom);

(async () => {
  await _loadMoreChatMessages();
  await stompConnected;

  sub1 = stompClient.subscribe(
    `/topic/chat/room/${chatRoomId}/messageCreated`,
    () => { loadMoreChatMessages(); }
  );

  sub2 = stompClient.subscribe(
    `/topic/chat/room/${chatRoomId}/systemMessageCreated`,
    (message) => {
      const newMessage = JSON.parse(message.body);
      setChatMessages((prevMessages) => [newMessage, ...prevMessages]);
    }
  );

  fetch(`/api/v1/chat/rooms/${chatRoomId}/messages/entry`, { method: "POST" });
})();

return () => {
  sub1?.unsubscribe();
  sub2?.unsubscribe();
  fetch(`/api/v1/chat/rooms/${chatRoomId}/messages/exit`, { method: "POST" });
};
```

**설명 포인트**
- `stompConnected` Promise — 연결은 앱당 1회지만 구독은 방마다. `await` 로 순서를 보장.
- `/topic/...` 은 브로커 목적지, `/app/...` 은 서버 핸들러 목적지.
  우리는 메시지 발행을 REST 로 하니 `/app` 은 안 쓴다.
- **여기서도 `messageCreated` 는 신호로만 쓰고 `systemMessageCreated` 만 데이터를 직접 쓴다.**
  ch07·ch09 와 완전히 같은 원칙 — 통신 수단만 바뀌었을 뿐.
- SockJS 는 WebSocket 이 막힌 환경을 위한 폴백 계층.

---

## ch12 — 마무리

세 방식 비교 표를 만들고 회고. 정리할 축:
요청 수 · 지연 · 방향성(단/양방향) · 커넥션 비용 · 브라우저 지원/재연결 ·
프록시 친화성 · 구현 복잡도 · 언제 무엇을 고를 것인가.

**관통하는 결론**: 통신 방식이 세 번 바뀌는 동안
**"커서로 다시 물어본다"** 는 데이터 획득 전략은 한 번도 안 바뀌었다.
실시간 통신에서 바뀌는 것은 **트리거**이지 **데이터 흐름**이 아니다.
