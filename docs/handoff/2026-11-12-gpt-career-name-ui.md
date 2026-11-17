# 선수 커리어 이름 입력 화면 시안

## v1 — 미채택

- 이미지: docs/mockups/career_name_screen_v1.png (1846×852px), 단일 화면 1칸.
- 프롬프트: docs/mockups/career_name_screen_v1_prompt.md
- 기준: create_screen.gd의 이름 입력·추천 이름·뒤로·입학하기, CLAUDE.md의 가로 모바일 및 SD 규칙.
- 사용자 피드백: AI 느낌이 강함, 추천 이름 삭제 요청. v2로 수정.
- 게임 적용은 Claude 담당. 코드 변경·테스트 실행 없음.

## v2 — 사용자 수정 반영

- 이미지: docs/mockups/career_name_screen_v2.png (1846×852px), 단일 화면 1칸.
- 프롬프트: docs/mockups/career_name_screen_v2_prompt.md
- 추천 이름 전부 삭제. 배경 디테일·광택·명암을 줄이고 펜화·평면 채색으로 수정.
- 왼쪽 선수·운동장, 오른쪽 이름 입력(최대 6자)·입학하기, 위 뒤로·게임명.
- 완성 화면 검토용이며 실제 UI와 그림은 적용 시 분리해야 한다. 기존 커리어가 있으면 덮어쓰기 경고를 유지해야 한다.
- 포지션은 현재 코드와 세계관 문서가 달라 시안에서 표기하지 않았다.
