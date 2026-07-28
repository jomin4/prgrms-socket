# prgrms-socket

스프링부트(Kotlin) + React 멀티 채팅방에 **폴링 → SSE → WebSocket(STOMP)** 를 단계적으로 도입하며
실시간 통신을 익히는 학습 프로젝트.

원본 강의: [slog.gg/p/14135](https://www.slog.gg/p/14135) · 원본 레포: [jhs512/p-14135-1](https://github.com/jhs512/p-14135-1)

## 실행

```bash
./gradlew bootRun
```

`http://localhost:8080` 접속. 채팅 확인은 탭 2개 이상 띄워서.

> 10장(HTTPS) 이후에는 `https://localhost:8080`, 그리고 `keystore.p12` 를 직접 생성해야 합니다.
> 인증서는 `.gitignore` 되어 있습니다.

## 진행 상황

챕터별 목표와 체크리스트는 [`docs/00-curriculum.md`](docs/00-curriculum.md).

각 챕터 완료 시점은 `ch00` `ch01` … 태그로 남아 있습니다.

```bash
git tag              # 챕터 목록
git checkout ch06    # 6장 시점 코드 보기
git checkout main    # 되돌아오기
```

## 스택

Kotlin · Spring Boot 4 (Web MVC) · Gradle KTS · React 19 (ESM CDN) · Tailwind CDN · SSE · STOMP/SockJS
