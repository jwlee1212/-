# 주자 시트 연결 인계

유저가 주자 전용 시트 적용을 요청했고, 담당 영역만 수정하고 중계 연결은 Claude에게 넘기도록 지정했다. 브랜치는 codex/runner-sprites. 실제 경기 호출부 연결은 남아 있다.

## 준비한 것

- scripts/sheets.json의 runners 정의와 tidy_sheet.py의 명시적 지면 기준점 지원.
- runners_ext PNG 원본·1/4·1/8 크기와 상대 팀 빨간색, 자동 생성 RunnersExtMeta.
- RunnerArt.draw_runner(canvas, cam, at, us, velocity, time, pose, face, slide_progress).
- 방향은 화면 속도 기준. 뒷모습·양쪽 뒤 사선·양쪽 앞 사선 각각 네 칸.
- slide_progress는 0~1일 때 헤드퍼스트 네 칸을 순서대로 선택. 기본 -1. pose만 slide이면 마지막 착지 칸 사용.
- 정지 시 기존 lead/onbase 헬멧 주자 그림 사용. 슬라이딩은 오른쪽 그림과 왼쪽 반전으로 표현한다.
- 같은 동작 행의 네 칸은 동일한 지면 기준점을 사용해 상대 위치와 발 들림을 보존했다.

## Claude가 연결할 곳

godot/scripts/batting/broadcast_view.gd의 _draw_play_runners에서 _draw_person 호출을 RunnerArt.draw_runner로 바꾸고 uniform == Tokens.UNIFORM_US를 전달한다. 수비수 호출은 현재 경로를 유지한다. _runner_pose가 slide를 선택한 판정의 tc를 이용해 (t - (tc - SLIDE_BEFORE_S)) / (SLIDE_BEFORE_S + SLIDE_AFTER_S)를 0~1로 제한해 slide_progress에 전달하면 시작부터 착지까지 재생된다. 관련 판정 선택 조건은 기존 것을 재사용한다.

같은 파일의 직접 주루 표시도 주자 그리기로 연결한다. screens/steal_view.gd의 주자 호출부는 전체 중계 연결과 함께 반영한다. 타석의 대기 주자는 기존 그림을 사용한다.

## 확인

res://tests/shots_runner_art.tscn은 24칸 전체를 우리 팀 큰 그림·상대 팀 작은 그림으로 보여준다. 최종 테스트 결과는 작업 채팅에 기록한다. 실제 경기에서의 방향 전환·슬라이딩 타이밍은 Claude 연결 후 확인해야 한다.
