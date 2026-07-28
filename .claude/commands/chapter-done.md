---
description: 현재 챕터를 커밋/태그/푸시하고 커리큘럼 상태를 갱신한다
argument-hint: <챕터번호> [제목]
---

챕터 $1 을 마무리한다.

1. `git status --short` 로 변경사항을 먼저 보여준다.
2. 제목이 $2 로 주어졌으면 그것을 쓰고, 없으면 `docs/00-curriculum.md` 의 챕터 $1 제목을 쓴다.
3. 다음을 실행한다.
   ```
   pwsh scripts/chapter-done.ps1 -Chapter $1 -Title "<제목>"
   ```
4. `docs/00-curriculum.md` 에서 챕터 $1 행의 상태를 ✅ 로 바꾸고, 다음 챕터를 🟡 로 바꾼다.
   (이 문서 변경분은 다음 챕터 커밋에 함께 실린다)
5. 다음 챕터에서 뭘 할지 두세 줄로 예고한다.
