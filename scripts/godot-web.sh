#!/usr/bin/env bash
# Godot 웹 빌드를 만들어 같은 와이파이의 아이폰에서 열 수 있게 띄운다.
#
#   ./scripts/godot-web.sh               # 개발 빌드 + 개발 서버 (8080)
#   PORT=9000 ./scripts/godot-web.sh
#   NO_SERVE=1 ./scripts/godot-web.sh    # 빌드만
#   RELEASE=1 ./scripts/godot-web.sh     # 출시용 빌드 (손맛 조절 패널 없음)
#
# 개발 빌드에는 왼쪽 아래 "⚙ 손맛" 패널이 있다. 슬라이더로 조절하고 [저장]하면 맥의 config/balance.json 에 기록된다.
# 개발 서버는 config/balance.json 원본을 그대로 보내므로, 파일을 고친 뒤 폰에서 새로고침만 하면 반영된다 (다시 빌드 불필요).
# 아이폰 사파리에서 출력된 주소(https://맥IP:포트)를 연다. 소리가 안 나면 무음 스위치를 끈다.
# Godot 4 웹 빌드는 HTTPS 에서만 실행되므로 개발 서버가 자체 서명 인증서로 HTTPS 를 띄운다.
# 처음 한 번 사파리가 "연결이 비공개가 아님" 경고를 띄우면: 세부사항 보기 → 이 웹 사이트 방문 → 웹 사이트 방문.
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${PORT:-8080}"
GODOT="${GODOT:-$HOME/Applications/Godot.app/Contents/MacOS/Godot}"

# 서버가 안 될 때를 대비한 복사본 (앱 안에 넣어 둔다)
mkdir -p godot/data godot/build/web
cp config/balance.json config/content.json godot/data/

"$GODOT" --headless --path godot --import >/dev/null 2>&1 || true
if [ -n "${RELEASE:-}" ]; then
  "$GODOT" --headless --path godot --export-release "Web" build/web/index.html
else
  "$GODOT" --headless --path godot --export-debug "Web" build/web/index.html
fi
[ -n "${NO_SERVE:-}" ] && { echo "빌드 완료: godot/build/web"; exit 0; }

IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")"
echo ""
echo "아이폰(같은 와이파이)에서 열기:  https://$IP:$PORT"
echo "  처음 한 번 '연결이 비공개가 아님' 경고 → 세부사항 보기 → 이 웹 사이트 방문 → 웹 사이트 방문"
echo "맥에서 열기:                    https://localhost:$PORT"
echo "수치 파일: config/balance.json (고친 뒤 폰에서 새로고침)"
echo "끝내려면 Ctrl+C"
echo ""
exec python3 scripts/dev_server.py "$PORT" "$IP"
