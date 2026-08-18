# prgrms-socket

스프링부트(Kotlin) + React 멀티 채팅방에 **폴링 → SSE → WebSocket(STOMP)** 를 단계적으로 도입하며
실시간 통신을 익히는 학습 프로젝트.

원본 강의: [slog.gg/p/14135](https://www.slog.gg/p/14135) · 원본 레포: [jhs512/p-14135-1](https://github.com/jhs512/p-14135-1)

## 실행

### 1. 인증서 생성 (최초 1회, 필수)

`keystore.p12` 는 개인키를 담고 있어 **커밋되지 않습니다.** 클론 후 직접 만들어야 합니다.
없으면 앱이 부팅에 실패합니다.

**프로젝트 루트**에서:

```bash
keytool -genkey -alias sb-ssl -storetype PKCS12 -keyalg RSA -keysize 2048 -keystore keystore.p12 -validity 3650
```

- 비밀번호는 **`123456`** (두 번 입력) — `application.yaml` 에 그 값으로 적혀 있습니다
- 이름·조직·국가 등은 **전부 빈칸으로 엔터**
- 마지막 `맞습니까?` 에 **`y`**

원리가 궁금하면 [`docs/qna/004-keytool-인증서-tls-원리.md`](docs/qna/004-keytool-인증서-tls-원리.md).

### 2. 서버 실행

```bash
./gradlew bootRun
```

**`https://localhost:8080`** 접속. 채팅 확인은 탭 2개 이상 띄워서.

> ⚠️ 자가서명 인증서라 브라우저 경고가 뜹니다 → **고급 → 계속 진행**. 정상입니다.
> `curl` 로 테스트할 때는 `-k` 옵션이 필요합니다.

## 진행 상황

**✅ ch00 ~ ch12 전체 완료** — 폴링에서 시작해 SSE 를 거쳐 WebSocket(STOMP)까지 도달했습니다.

- 챕터별 목표와 체크리스트 → [`docs/00-curriculum.md`](docs/00-curriculum.md)
- **최종 회고와 세 방식 비교** → [`docs/04-retrospective.md`](docs/04-retrospective.md)
- 학습 중 나온 질문 기록 → [`docs/qna/`](docs/qna/README.md)

각 챕터 완료 시점은 `ch00` `ch01` … 태그로 남아 있습니다.

```bash
git tag              # 챕터 목록
git checkout ch06    # 6장 시점 코드 보기
git checkout main    # 되돌아오기
```

## 스택

Kotlin · Spring Boot 4 (Web MVC) · Gradle KTS · React 19 (ESM CDN) · Tailwind CDN · SSE · STOMP/SockJS
