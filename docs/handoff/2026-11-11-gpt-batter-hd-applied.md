# 고해상도 타자 인게임 적용 완료

사용자가 이번 요청에서 인게임 교체까지 직접 지시하여, 이미지 전용 업무 범위를 넘어 아래 적용 작업을 수행했다.

- `scripts/sheets.json` 타자 정의를 고해상도 두 칸 원본 6장으로 변경. 게임의 20개 이름 칸을 12개 원본 포즈에 대응시켰다. 숨쉬기·번트는 원본에 없어 준비 자세를 임시 사용한다.
- `scripts/tidy_sheet.py`에 두 칸 원본 목록 입력·거의 투명한 배경 제거·발끝 높이 정렬·격자 출력 기능을 추가. 재생성: `/private/tmp/claude-501/venv/bin/python scripts/tidy_sheet.py batter` (Pillow·NumPy 필요).
- 생성 결과 `godot/art/pixel/batter_ext.png`: **3365×3924**, 5열×4행, **칸당 673×981**. 원본 그림을 축소하지 않고 투명 여백만 공통으로 잘랐다. 메타도 스크립트로 재생성했다.
- `batter_sprite.gd`가 메타의 열 수로 행·열 위치를 계산하도록 변경. 기존 타격 이름 인덱스와 호출부는 유지한다.
- 컨택 원본 좌표 (700,510), 생성 메타 발 기준 (350,-496). `batting_view.gd`에서 컨택점을 존에 맞추면서 발이 화면 아래로 잘리지 않도록 최대 배율을 제한했다. 텍스처 임포트는 기존 무손실·밉맵·크기 제한 없음 설정을 유지.
- Godot 리소스 임포트 완료. 화면 캡처용 `SHOT_SCALE=3 ...Godot --always-on-top --path godot res://tests/shots_batter.tscn`을 실행해 2532×1170 캡처의 준비·컨택·팔로스루·복귀를 확인했다. 실행 로그에 스크립트 오류 없이 종료. 게임 로직 테스트는 실행하지 않았다.
- 보존 캡처: `docs/refs/screens/2026-11-11-batter-hd-ready.png`, `2026-11-11-batter-hd-contact.png`.

현재 worktree 적용 완료이며 main 합치기·커밋·푸시는 하지 않았다. 이전 `2026-11-11-gpt-batter-hd.md`의 '게임 적용 전' 상태는 이 메모로 대체한다. 고해상도 원본 프롬프트는 그대로 보존했다.
