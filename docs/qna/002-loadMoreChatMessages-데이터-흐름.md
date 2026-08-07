# Q002. `loadMoreChatMessages` 안에 실제로 어떤 값이 들어오고 어떻게 처리되나?

**챕터**: ch04 · **날짜**: 2026-08-07

대상 코드:

```jsx
if (data.length > 0) {
  data.reverse();
  lastChatMessageId.current = data[0].id;
  setChatMessages((chatMessages) => [...data, ...chatMessages]);
}
```

---

## 전제: 서버의 초기 데이터 (ch03)

`++lastChatMessageId` 가 **방을 가로질러** 증가하므로 id 는 이렇게 깔립니다.

| 방 | 메시지 id | 내용 |
|---|---|---|
| 1번 방 | 1, 2 | 김철수 "풋살하실 분 계신가요?" / 이영희 "네, 저요!" |
| **2번 방** | **3, 4** | 박철수 "농구하실 분 계신가요?" / 김영희 "네, 저요!" |
| 3번 방 | 5, 6 | 이철수 / 박영희 |

프론트는 `chatRoomId = 2` 로 고정이므로 **3번, 4번**을 다루게 됩니다.

---

## T0 — 페이지를 처음 열었을 때

**커서**: `lastChatMessageId.current = 0` (useRef 초기값)

**요청**
```
GET /api/v1/chat/rooms/2/messages?afterChatMessageId=0
```

**서버 처리** (ch03 `getItems`)
```kotlin
chatMessagesByRoomId[2]            // [id=3, id=4]
    ?.filter { it.id > 0 }         // 둘 다 통과
```

**응답 `data`** — 오름차순(옛날→최신)
```js
[
  { id: 3, createDate: "2026-08-07T16:08:03.41", chatRoomId: 2, writerName: "박철수", content: "농구하실 분 계신가요?" },
  { id: 4, createDate: "2026-08-07T16:08:03.41", chatRoomId: 2, writerName: "김영희", content: "네, 저요!" }
]
```

### 네 줄이 차례로 하는 일

**① `data.length > 0`** → `2 > 0` → **참**, 블록 진입

**② `data.reverse()`** — 배열을 **뒤집는다. 원본을 직접 뒤집는다** (새 배열을 만들지 않음)
```js
[ {id:3}, {id:4} ]   →   [ {id:4}, {id:3} ]
                          ↑ 이제 [0]번이 가장 큰 id
```

**③ `lastChatMessageId.current = data[0].id`**
```js
data[0]      // { id: 4, ... }
data[0].id   // 4
```
→ `lastChatMessageId.current` 가 `0` → **`4`** 로 갱신.
`useRef` 라서 **이 줄에서 즉시** 반영됩니다 (리렌더 안 기다림).

**④ `setChatMessages((chatMessages) => [...data, ...chatMessages])`**

여기서 `chatMessages` 는 **React 가 넣어주는 최신 상태값**입니다.
(바깥 변수와 이름이 같지만 **가려집니다** — 파라미터가 우선)

```js
chatMessages (이전 상태) = []          // useState([]) 초기값
data                     = [ {id:4}, {id:3} ]

[...data, ...chatMessages]
= [ {id:4}, {id:3} ]                   // 새 상태
```

**화면**
```
4(26.08.07 16:08) : 김영희 : 네, 저요!
3(26.08.07 16:08) : 박철수 : 농구하실 분 계신가요?
```

---

## T1 — 1초 뒤, 아무도 말을 안 했을 때

**요청**
```
GET /api/v1/chat/rooms/2/messages?afterChatMessageId=4
```

**서버** — `filter { it.id > 4 }` → 해당 없음

**응답**
```js
[]
```

**① `data.length > 0`** → `0 > 0` → **거짓** → **블록 전체를 건너뜀**

- 커서 그대로 `4`
- `setChatMessages` 호출 안 됨 → **리렌더도 안 일어남**
- 화면 변화 없음

> ⚠️ **이 상태가 1초마다 무한 반복됩니다.**
> 조용한 방에서는 폴링 요청의 **100%가 `[]`** 를 받습니다.
> 5~7장에서 SSE 로 없앨 낭비가 정확히 이것.
>
> 그리고 `if` 가드가 왜 필수인지도 여기서 보입니다 — 빈 배열에서 `data[0].id` 를 읽으면
> `Cannot read properties of undefined` 로 죽습니다.

---

## T2 — 다른 탭에서 "안녕하세요" 를 작성

**서버**: `++lastChatMessageId` → **7** (1~6은 seed) → 2번 방 리스트 = `[3, 4, 7]`

**다음 폴링 요청**
```
GET /api/v1/chat/rooms/2/messages?afterChatMessageId=4
```

**응답**
```js
[ { id: 7, chatRoomId: 2, writerName: "홍길동", content: "안녕하세요" } ]
```

① `1 > 0` → 진입
② `reverse()` → 원소가 1개라 그대로 `[ {id:7} ]`
③ `data[0].id = 7` → 커서 `4` → **`7`**
④ 이전 상태 `[ {id:4}, {id:3} ]`
```js
[...data, ...chatMessages]
= [ {id:7}, {id:4}, {id:3} ]
```

**화면** — 새 메시지가 **맨 위**에 추가
```
7(...) : 홍길동 : 안녕하세요      ← 새로 추가
4(...) : 김영희 : 네, 저요!
3(...) : 박철수 : 농구하실 분 계신가요?
```

---

## T3 — 한 주기 안에 메시지가 2개 몰렸을 때

1초 사이에 8번, 9번이 연달아 작성됐다고 하면:

**응답** (오름차순)
```js
[ {id:8, content:"저요"}, {id:9, content:"저도요"} ]
```

② `reverse()` → `[ {id:9}, {id:8} ]`
③ `data[0].id = 9` → 커서 **9** ✅ (가장 큰 값)
④ `[ {9}, {8} ]` + `[ {7}, {4}, {3} ]` = `[ 9, 8, 7, 4, 3 ]`

**목록 전체가 여전히 내림차순으로 유지됩니다.** 이게 `reverse()` 의 두 번째 목적입니다.

---

## `reverse()` 를 빼면 실제로 무슨 일이 나나

T0 을 `reverse()` 없이 다시 돌려보면:

```js
data = [ {id:3}, {id:4} ]
data[0].id = 3            // ← 가장 큰 값이 아니라 가장 작은 값!
lastChatMessageId.current = 3
setChatMessages(...)  →  [ {id:3}, {id:4} ]   // 목록이 오름차순(옛날이 위)
```

다음 폴링:
```
GET ...?afterChatMessageId=3     // 4번을 아직 못 받은 걸로 착각
→ [ {id:4} ]                     // 4번을 또 받음
→ 상태 = [ {id:4}, {id:3}, {id:4} ]   // 4번이 두 번!
```

- **중복 메시지**가 화면에 뜨고
- `key={chatMessage.id}` 가 겹쳐서 React 가 **`Encountered two children with the same key`** 경고를 뱉습니다.

즉 `reverse()` 는 두 가지를 동시에 합니다.

1. **`data[0]` 을 "가장 큰 id" 로 만든다** → 커서가 정확해짐 (중복 방지)
2. **새 뭉치를 내림차순으로 만든다** → `[...data, ...prev]` 로 앞에 붙였을 때 전체 정렬 유지

---

## 상태 변화 요약

| 시점 | 요청 커서 | 응답 `data` | reverse 후 | 새 커서 | `chatMessages` |
|---|---|---|---|---|---|
| T0 | 0 | `[3, 4]` | `[4, 3]` | 4 | `[4, 3]` |
| T1 | 4 | `[]` | — | 4 (유지) | `[4, 3]` (변화 없음) |
| T2 | 4 | `[7]` | `[7]` | 7 | `[7, 4, 3]` |
| T3 | 7 | `[8, 9]` | `[9, 8]` | 9 | `[9, 8, 7, 4, 3]` |

---

## 왜 "가장 큰 id" 하나만 기억하면 충분한가

ch03 에서 id 를 **전역 단조증가**로 만들었기 때문입니다.
`++lastChatMessageId` 가 방과 무관하게 1, 2, 3, … 으로만 올라가므로

> **"내가 가진 최대 id 보다 큰 것 = 내가 아직 못 받은 것"**

이 항상 성립합니다. 받은 id 목록을 다 들고 있을 필요가 없어요.

이 단순한 규칙이 **폴링 → SSE → STOMP 로 통신 방식이 세 번 바뀌어도
데이터를 가져오는 코드는 그대로 두게** 해주는 기반입니다.
바뀌는 건 "언제 물어보는가(트리거)" 뿐입니다.
