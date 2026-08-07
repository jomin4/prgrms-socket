# Q001. Babel 은 브라우저에서 React 코드를 어떻게 변환하나?

> 브라우저는 HTML 코드만 이해하는 거 아니었나?

**챕터**: ch04 · **날짜**: 2026-08-07

---

## 1. 전제부터 바로잡기 — 브라우저는 세 가지를 이해한다

| 언어 | 담당 | 처리 엔진 |
|---|---|---|
| HTML | 구조 | HTML 파서 |
| CSS | 스타일 | CSS 엔진 |
| **JavaScript** | **동작** | **JS 엔진 (Chrome=V8, Safari=JSC)** |

브라우저 안에는 **완전한 JS 실행기**가 들어 있습니다. JVM 이 `.class` 를 실행하듯,
V8 이 JS 를 파싱해서 기계어로 컴파일하고 실행합니다.

즉 브라우저가 못 읽는 건 "JS" 가 아니라 **JSX** 입니다.

## 2. 브라우저가 못 읽는 건 JSX 뿐이다

```jsx
return <h1 className="font-bold">채팅방</h1>;
```

JSX 는 **JavaScript 가 아닙니다.** 표준 JS 문법에 `<h1>` 같은 태그를 쓰는 규칙은 없습니다.
V8 에 그대로 넣으면 `Unexpected token '<'` 로 죽습니다.

JSX 는 React 팀이 만든 **JS 확장 문법**이고, 반드시 표준 JS 로 번역돼야 실행됩니다.

## 3. 번역 결과는 그냥 함수 호출이다

```jsx
// 번역 전 (JSX)
<h1 className="font-bold">채팅방</h1>

// 번역 후 (표준 JS)
React.createElement("h1", { className: "font-bold" }, "채팅방")
```

중첩도 똑같이 중첩된 함수 호출이 됩니다.

```jsx
<ul>
  <li key={1}>안녕</li>
</ul>
```
```js
React.createElement("ul", null,
  React.createElement("li", { key: 1 }, "안녕")
)
```

**여기서 `import React` 가 왜 필요한지 드러납니다.** 번역 결과가 `React.createElement` 를
쓰기 때문에, `React` 라는 이름이 그 파일 스코프에 있어야 합니다.
(내 코드에는 `React` 를 직접 쓰는 곳이 한 군데도 없는데 import 하는 이유가 이것)

## 4. @babel/standalone 도 그냥 JS 프로그램이다

핵심은 이겁니다 — **Babel 은 브라우저의 기능이 아니라, 브라우저 위에서 도는 JS 라이브러리**입니다.

"문자열을 받아서 다른 문자열을 뱉는 번역기" 를 JS 로 짠 것뿐이에요.
원래 이 번역기는 개발자 PC 에서 (Node.js 로, 빌드할 때) 돌립니다.
`@babel/standalone` 은 그 번역기를 **브라우저에서도 돌 수 있게** 묶은 버전입니다.

## 5. 실제 실행 순서

```html
<script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
<script type="text/babel" data-type="module">
  import React from "https://esm.sh/react@19";
  ...
</script>
```

1. **브라우저가 HTML 을 위에서부터 파싱**한다.

2. 첫 번째 `<script>` — `type` 이 없으면 기본이 JS.
   → 브라우저가 정상 실행 → **`Babel` 이라는 전역 객체가 메모리에 올라간다.**

3. 두 번째 `<script type="text/babel">` — 브라우저는 `text/babel` 이라는 타입을 **모른다.**
   → **실행하지 않고 무시한다.** (에러도 안 남)
   → 하지만 **DOM 트리에는 그대로 남아 있다.** 그 안의 코드는 아직 "그냥 텍스트" 일 뿐.

4. 문서 로드가 끝나면 **Babel 이 스스로 깨어나서** 문서를 훑는다.
   `document.querySelectorAll('script[type="text/babel"]')` 로 자기가 처리할 스크립트를 찾는다.

5. 찾은 스크립트의 **텍스트 내용을 문자열로 읽어서** 번역한다.
   - 파싱 → AST(구문 트리) 생성
   - JSX 노드를 `React.createElement(...)` 호출 노드로 치환
   - 다시 코드 문자열로 출력

6. **번역된 코드로 새 `<script>` 엘리먼트를 만들어 문서에 붙인다.**
   이번엔 브라우저가 아는 타입이므로 → **V8 이 실행한다.**

```
HTML 파싱
   ↓
babel.min.js 실행         → Babel 준비됨
   ↓
type="text/babel" 무시    → 코드는 텍스트로 대기
   ↓
Babel 이 그 텍스트를 찾음
   ↓
JSX → React.createElement 로 번역
   ↓
새 <script> 로 주입       → 브라우저가 실행
   ↓
React 가 #root 에 DOM 생성
```

## 6. `data-type="module"` 이 필요한 이유

`import` 문은 **모듈 스크립트에서만** 동작합니다 (`<script type="module">`).

`data-type="module"` 을 주면 Babel 이 6단계에서 주입할 때 **모듈 타입으로** 만들어 줍니다.
빠뜨리면 일반 스크립트로 주입되고, 그 안의 `import` 는:

```
Uncaught SyntaxError: Cannot use import statement outside a module
```

## 7. 왜 실무에선 이렇게 안 하나

| | 브라우저에서 변환 (지금) | 빌드 타임 변환 (실무) |
|---|---|---|
| 번역 시점 | **사용자가 페이지 열 때마다** | 배포 전 한 번 |
| 다운로드 | Babel 자체가 수백 KB | 0 (번역 결과만 배포) |
| 첫 화면 | 번역 끝나야 뜸 (느림) | 바로 뜸 |
| 용도 | **학습 · 프로토타입** | 실서비스 |

우리 관심사는 실시간 통신이지 프론트 빌드 파이프라인이 아니라서,
`npm install` / Vite 설정 없이 **HTML 파일 하나**로 끝내려고 이 방식을 택했습니다.

---

## 한 줄 요약

브라우저는 JS 를 이해한다. 못 읽는 건 **JSX** 뿐이고,
**Babel(그냥 JS 라이브러리)** 이 페이지 로드 후 그 JSX 텍스트를 찾아
`React.createElement(...)` 호출로 번역한 뒤 새 `<script>` 로 주입해서 실행시킨다.
