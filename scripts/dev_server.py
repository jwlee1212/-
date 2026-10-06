#!/usr/bin/env python3
"""폰 테스트용 개발 서버 (CLAUDE.md §3-7: 손맛 수치를 다시 빌드하지 않고 바꾼다).

- 웹 빌드(godot/build/web)를 내보낸다
- /balance.json, /content.json 은 저장소의 원본 config/ 파일을 그때그때 읽어 보낸다
  → 맥에서 파일을 고치고 폰에서 새로고침하면 바로 반영
- POST /balance.json {"patch": {"batting.timing.inputLatencyMs": 40, ...}}
  → 디버그 패널의 [저장]. 이미 있는 항목만 고친다 (없는 경로는 거부)
- 모든 응답에 캐시 금지 헤더를 붙인다 (아이폰 사파리가 옛 빌드를 붙잡고 있지 않게)
- HTTPS 로 띄운다. Godot 4 웹 빌드는 보안 연결(HTTPS 또는 localhost)에서만 실행되기 때문이다.
  인증서는 처음 실행할 때 맥 IP 로 자체 서명해서 godot/build/certs/ 에 만든다 (와이파이가 바뀌어 IP 가 달라지면 새로 만든다).
  자체 서명이라 아이폰 사파리가 처음 한 번 경고한다: "세부사항 보기 → 이 웹 사이트 방문" 으로 넘어간다

    python3 scripts/dev_server.py [포트] [맥 IP]
같은 와이파이 안에서만 쓴다. 고칠 수 있는 파일은 config/balance.json 하나뿐이다.
"""
import http.server
import json
import os
import ssl
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "godot", "build", "web")
# 테스트할 때는 BALANCE_PATH 로 다른 파일을 가리킬 수 있다
CONFIG = os.environ.get("BALANCE_PATH", os.path.join(ROOT, "config", "balance.json"))


def apply_patch(data, patch):
    missing = []
    for path, value in patch.items():
        keys = path.split(".")
        cur = data
        for k in keys[:-1]:
            if isinstance(cur, dict) and k in cur:
                cur = cur[k]
            else:
                cur = None
                break
        if isinstance(cur, dict) and keys[-1] in cur and isinstance(value, (int, float)) and not isinstance(value, bool):
            cur[keys[-1]] = value
        else:
            missing.append(path)
    return missing


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIST, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def _is_balance(self):
        return self.path.split("?")[0].endswith("/balance.json")

    def _live_config(self):
        """원본에서 바로 보내는 config 파일 경로 (없으면 None)"""
        name = self.path.split("?")[0].rsplit("/", 1)[-1]
        if name == "balance.json":
            return CONFIG
        if name in ("content.json",):
            return os.path.join(ROOT, "config", name)
        return None

    def _reply(self, code, body, content_type="application/json; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        live = self._live_config()
        if live:
            with open(live, encoding="utf-8") as f:
                self._reply(200, f.read())
        else:
            super().do_GET()

    def do_POST(self):
        if not self._is_balance():
            self._reply(404, '{"error": "not found"}')
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            patch = json.loads(self.rfile.read(length).decode("utf-8"))["patch"]
            with open(CONFIG, encoding="utf-8") as f:
                data = json.load(f)
            missing = apply_patch(data, patch)
            if missing:
                self._reply(400, json.dumps({"error": "없는 항목", "missing": missing}, ensure_ascii=False))
                return
            with open(CONFIG, "w", encoding="utf-8") as f:
                f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
            print(f"[저장] config/balance.json ← {', '.join(f'{k}={v}' for k, v in patch.items())}")
            self._reply(200, json.dumps({"ok": True, "changed": len(patch)}))
        except Exception as e:  # 형식이 틀린 요청
            self._reply(400, json.dumps({"error": str(e)}, ensure_ascii=False))


def ensure_cert(ip):
    """맥 IP 와 localhost 용 자체 서명 인증서. 이미 있으면 그대로 쓴다"""
    cert_dir = os.path.join(ROOT, "godot", "build", "certs")
    os.makedirs(cert_dir, exist_ok=True)
    cert = os.path.join(cert_dir, f"dev-{ip}.pem")
    key = os.path.join(cert_dir, f"dev-{ip}.key")
    if not (os.path.exists(cert) and os.path.exists(key)):
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
            "-keyout", key, "-out", cert, "-days", "365",
            "-subj", "/CN=baseball-gm dev server",
            "-addext", f"subjectAltName=IP:{ip},IP:127.0.0.1,DNS:localhost",
        ], check=True, capture_output=True)
    return cert, key


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ip = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1"
    cert, key = ensure_cert(ip)
    server = http.server.ThreadingHTTPServer(("0.0.0.0", port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cert, key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()
