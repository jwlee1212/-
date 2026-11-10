# 경기 아트 인수인계

작업 브랜치는 `codex/stadium-views`다. 이 브랜치의 적용 코드와 원본을 함께 확인한다.

- **타석 적용본**은 새 타석 배경 `batting_stadium_2d_v2.png`와 정면 대기 수비수 `fielder_batting_ready_v1.png`를 쓴다.
- **중계 적용본**은 수비수·주자 시트를 연결했지만 배경은 기존 가로형 `broadcast_stadium_2d_v1.png`를 쓴다. 사용자 요청으로 새로 만든 **정사각형 `broadcast_stadium_2d_v2.png`**를 다음 적용본으로 쓴다. 가로 화면으로 늘리지 않고, 하나의 배율로 원본을 잘라 공을 따라 이동한다. [카메라 기준점](2026-11-10-gpt-broadcast-stadium-v2.md)을 참고한다.
- **타격 효과 채택본**은 사용자가 승인한 `docs/refs/sprites/batting_impact_2d_v1.png`다. 724×724px 3칸이며 왼쪽부터 빗맞음, 정타, 홈런이다. 현재는 원본과 프롬프트가 준비됐고 인게임 연결은 남아 있다. [타격 효과 적용 메모](2026-11-10-gpt-batting-impact.md)를 참고한다.
- 적용 코드의 타석·도루 캡처는 `godot/build/shots/hud-4-speed.png`, `godot/build/shots/steal-4-run.png`에서 확인했다. 위 두 후속 원본은 캡처에 반영되기 전이다.
