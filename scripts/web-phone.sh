#!/usr/bin/env bash
# 웹(Wasm) 빌드를 만들어 같은 와이파이의 아이폰에서 열 수 있게 띄운다 (CLAUDE.md §2 테스트 기기 ①).
#
#   ./scripts/web-phone.sh          # 빌드 + 8080 포트로 서비스
#   PORT=9000 ./scripts/web-phone.sh
#
# 아이폰 사파리에서 출력된 주소(http://맥IP:포트)를 연다. 공유 → "홈 화면에 추가" 하면 앱처럼 뜬다.
# 세이브는 그 브라우저의 localStorage 에 남는다 (주소가 바뀌면 새 게임부터).
set -euo pipefail
cd "$(dirname "$0")/.."
PORT="${PORT:-8080}"

./gradlew :app:wasmJsBrowserDistribution -q
DIST="app/build/dist/wasmJs/productionExecutable"

IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "맥IP")"
echo ""
echo "아이폰(같은 와이파이)에서 열기:  http://$IP:$PORT"
echo "맥에서 열기:                    http://localhost:$PORT"
echo "끝내려면 Ctrl+C"
echo ""
# python 의 http.server 는 .wasm 을 application/wasm 으로 보내 준다 (Wasm 로딩에 필요)
exec python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$DIST"
