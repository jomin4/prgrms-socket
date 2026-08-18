# 커리큘럼 — 폴링 → SSE → WebSocket(STOMP)

원본 강의: [slog.gg/p/14135](https://www.slog.gg/p/14135) (장희성)
원본 백엔드 레포: [jhs512/p-14135-1](https://github.com/jhs512/p-14135-1)

---

## 학습 방식

한 챕터는 항상 이 순서로 돕니다.

1. **강사(Claude)가 코드 제공** — 이번 챕터에서 추가/수정할 코드 전체
2. **구체적 설명** — 왜 이렇게 쓰는지, 각 줄이 무슨 일을 하는지, 어떤 함정이 있는지
3. **내가 직접 타이핑** — 복붙 금지. 손으로 친다.
4. **동작 확인** — 실행해서 눈으로 본다
5. **원격 반영 승인** — 강사가 "반영할까요?" 물으면 승인 → 자동 커밋/태그/푸시
6. 다음 챕터

성장형이므로, 진행하면서 챕터가 쪼개지거나 추가될 수 있습니다.
추가되면 이 파일에 append 하고 커밋합니다.

---

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Kotlin + Spring Boot 4 (Web MVC), Gradle Kotlin DSL |
| 저장소 | 없음. 인메모리 `MutableList` / `ConcurrentHashMap` (실시간 통신에 집중) |
| 프론트 | React 19 (ESM CDN) + Babel Standalone + Tailwind CDN, 단일 HTML 파일 |
| 서빙 | Spring Boot 정적 리소스 (`src/main/resources/static/index.html`) |
| 실시간 | 폴링 → SSE(`SseEmitter` / `EventSource`) → WebSocket(STOMP + SockJS) |

---

## 챕터 목록

| # | 제목 | 핵심 개념 | 태그 | 상태 |
|---|---|---|---|---|
| 00 | 학습 환경 세팅 | 레포/자동화 스크립트/커리큘럼 | `ch00` | ✅ |
| 01 | 스프링부트 프로젝트 뼈대 | Gradle KTS, `@SpringBootApplication`, application.yaml | `ch01` | ✅ |
| 02 | 채팅방 도메인 + REST API | data class, `@RestController`, `@CrossOrigin`, 인메모리 저장 | `ch02` | ✅ |
| 03 | 채팅 메시지 API | `afterChatMessageId` 증분 조회, 메시지 작성 | `ch03` | ✅ |
| 04 | 프론트 — 폴링 방식 채팅방 | React ESM, `useState`/`useRef`/`useEffect`, `setInterval` 폴링 | `ch04` | ✅ |
| 05 | 폴링 vs SSE — 개념과 비용 ([문서](03-polling-vs-sse.md)) | 요청 수, 지연, 커넥션 유지, 언제 뭘 쓰나 | `ch05` | ✅ |
| 06 | 백엔드 SSE — SseEmitters | `SseEmitter`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, 커넥션 정리 | `ch06` | ✅ |
| 07 | 프론트 SSE — EventSource | 잘못된 방식(데이터 직접 수신) vs 옳은 방식(신호로만 사용) | `ch07` | ✅ |
| 08 | 미션2 — 쓰로틀링 | throttle vs debounce, `useCallback` + lodash `throttle` | `ch08` | ✅ |
| 09 | 미션3 — 입장/퇴장 시스템 메시지 | 이벤트 순서 문제, cleanup 함수, 채팅방 이동 | `ch09` | ✅ |
| 10 | 로컬 HTTPS 적용 | `keytool`, PKCS12, `server.ssl.*` | `ch10` | ✅ |
| 11 | 미션4 — WebSocket(STOMP) 도입 | `@EnableWebSocketMessageBroker`, SimpleBroker, SockJS, `/topic` | `ch11` | ✅ |
| 12 | 세 방식 비교 정리 + 회고 ([문서](04-retrospective.md)) | 폴링/SSE/WebSocket 트레이드오프 표 | `ch12` | ✅ |

> 사용자는 백엔드(Kotlin/Spring) 기반이고 **JS·React 는 익숙하지 않다.**
> 프론트 코드를 설명할 때는 **그 코드에 실제로 등장하는 문법**을
> (화살표 함수, 템플릿 리터럴, 스프레드, `.then` 체인, 훅 등) 그 자리에서 풀어서 설명한다.
> 별도의 문법 강의 챕터는 만들지 않는다.

상태: ⬜ 대기 · 🟡 진행중 · ✅ 완료

---

## 원본 강의와 다른 점 (의도적)

1. **프론트를 CodePen이 아니라 레포 안 정적 파일로 둠**
   → 내가 친 프론트 코드도 전부 git 에 남고, CORS·HTTPS 이슈를 피할 수 있음.
   단, 강의와 동일하게 `@CrossOrigin` 은 그대로 배웁니다 (외부 오리진 대응용).
2. **JDK 21 툴체인** — 내 로컬에 설치된 JDK 기준.
3. **`keystore.p12` 는 커밋하지 않음** — `.gitignore` 처리. 10장에서 직접 생성.

---

## 실행 방법

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` (10장 이후 `https://localhost:8080`).
채팅 테스트는 **탭 2개 이상** 띄워서 확인합니다.
