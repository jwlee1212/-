#!/usr/bin/env bash
# Godot 웹 빌드를 만들어 같은 와이파이의 아이폰에서 열 수 있게 띄운다.
#
#   ./scripts/godot-web.sh          # 빌드 + 8080 포트로 서비스
#   PORT=9000 ./scripts/godot-web.sh
#   NO_SERVE=1 ./scripts/godot-web.sh   # 빌드만
#
# 아이폰 사파리에서 출력된 주소(http://맥IP:포트)를 연다. 소리가 안 나면 무음 스위치를 끈다.
# 웹 빌드는 스레드를 쓰지 않는 판(nothreads)이라 특별한 서버 설정 없이 열린다.
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${PORT:-8080}"
GODOT="${GODOT:-$HOME/Applications/Godot.app/Contents/MacOS/Godot}"

# 수치 원본은 config/balance.json 하나다. 빌드 직전에 Godot 프로젝트 안으로 복사한다
mkdir -p godot/data godot/build/web
cp config/balance.json godot/data/balance.json

"$GODOT" --headless --path godot --import >/dev/null 2>&1 || true
"$GODOT" --headless --path godot --export-release "Web" build/web/index.html
DIST="godot/build/web"
[ -n "${NO_SERVE:-}" ] && { echo "빌드 완료: $DIST"; exit 0; }

IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "맥IP")"
echo ""
echo "아이폰(같은 와이파이)에서 열기:  http://$IP:$PORT"
echo "맥에서 열기:                    http://localhost:$PORT"
echo "끝내려면 Ctrl+C"
echo ""
exec python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$DIST"
