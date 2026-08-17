# Q003. PowerShell 에서 `curl -d "{\"key\":\"value\"}"` 가 400 에러

**챕터**: ch06 · **날짜**: 2026-08-18

---

## 증상

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/chat/rooms/2/messages" `
  -H "Content-Type: application/json" -d "{\"writerName\":\"홍길동\",\"content\":\"안녕\"}"
```

에러가 **두 개** 난다.

```
400 Bad Request
JSON parse error: Unexpected character ('\' (code 92)):
  was expecting double-quote to start property name
  at [Source: ...; byte offset: #1]

curl: (3) URL rejected: Port number was not a decimal number between 0 and 65535
```

---

## 원인 — PowerShell 의 이스케이프 문자는 백슬래시가 아니다

PowerShell 의 이스케이프 문자는 **백틱(`` ` ``)** 이다. 백슬래시(`\`)는 그냥 평범한 글자다.

그래서 `"{\"writerName\":..."` 를 이렇게 읽는다.

```
"{\"writerName\":\"홍길동\"}"
 ↑  ↑
 │  여기서 문자열이 닫힘 (\ 는 글자, " 는 종료)
 열림
```

결과적으로 **하나의 인자가 두 개로 쪼개진다.** 실제로 확인하면:

```
[5] -d
[6] {\
[7] writerName\:\hong\,\content\:\hi\}
```

이것이 두 에러를 정확히 설명한다.

| 에러 | 원인 |
|---|---|
| `Unexpected character ('\' code 92) ... byte offset: #1` | curl 이 본문으로 `{\` 만 받음 → Jackson 이 2번째 글자에서 실패 |
| `curl: (3) URL rejected: Port number was not...` | 잘려나간 `writerName\:...` 조각을 **두 번째 URL** 로 오해 |

`byte offset: #1` 이 결정적 단서다. 본문이 사실상 2바이트뿐이었다는 뜻.

---

## 해결

### 방법 1 — 홑따옴표 (권장)

```powershell
curl.exe -s -X POST "http://localhost:8080/api/v1/chat/rooms/2/messages" `
  -H "Content-Type: application/json" -d '{"writerName":"홍길동","content":"안녕"}'
```

PowerShell 의 **홑따옴표 문자열은 내용을 그대로** 넘긴다(변수 확장도 이스케이프 해석도 없음).
백슬래시가 아예 필요 없다. 한글도 정상 전송된다.

### 방법 2 — Invoke-RestMethod (PowerShell 네이티브)

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/chat/rooms/2/messages" `
  -Method Post -ContentType "application/json; charset=utf-8" `
  -Body '{"writerName":"홍길동","content":"안녕"}'
```

응답이 문자열이 아니라 **객체**로 나와서 `.id` 처럼 바로 꺼내 쓸 수 있다.

---

## 셸별 정리

| 셸 | JSON 넘기는 법 |
|---|---|
| **cmd** | `-d "{\"key\":\"value\"}"` — 백슬래시 이스케이프 |
| **PowerShell** | `-d '{"key":"value"}'` — **홑따옴표** |
| bash / git bash | `-d '{"key":"value"}'` — 동일 |

**PowerShell 과 bash 는 같고, cmd 만 다르다.**
강의 자료나 블로그의 `\"` 형태를 PowerShell 에 그대로 붙이면 항상 이 에러가 난다.

> JSON 안에 홑따옴표가 들어가야 하면 두 번 쓴다: `'{"content":"it''s"}'`
> 그런 경우가 잦으면 `Invoke-RestMethod` 나 here-string 이 편하다.

---

## 참고 — `curl` 이 아니라 `curl.exe` 를 쓰는 이유

PowerShell 에서 `curl` 은 `Invoke-WebRequest` 의 **별칭**이다(Windows PowerShell 5.1 기준).
`-X`, `-H`, `-d` 같은 curl 옵션을 이해하지 못한다.
진짜 curl 을 부르려면 반드시 **`curl.exe`** 라고 확장자까지 적어야 한다.
