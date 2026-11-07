#!/usr/bin/env python3
"""외부 소리 가져오기 (2026-11-07): ElevenLabs 에서 받은 배경음악·심판 콜 원본을 게임용 파일로 가공한다.

    .venv/bin/python scripts/import_audio.py ~/Desktop/audio     (numpy·soundfile 필요)

원본은 저장소에 넣지 않는다 (곡당 20MB 넘음). 원본 폴더는 유저 맥에 두고(백업 권장), 이 스크립트로 만든 결과만 넣는다.
원본 이름 → 게임 파일 (FILES 표, 같은 소리에 이름이 여럿이면 처음 찾은 것):
    lobby.wav            → godot/audio/bgm/lobby.ogg   로비 곡
    main.wav / home.wav  → godot/audio/bgm/home.ogg    홈 곡
    crowd.wav / game.wav → godot/audio/bgm/game.ogg    경기장 소리 (음악 없음)
    umpire*strike.mp3    → godot/audio/sfx/call_strike.wav     등 심판 콜

배경음악이 하는 일:
  1) 루프 이음매: ElevenLabs 곡은 끝이 페이드아웃 없이 뚝 잘려 있어 그대로 돌리면 끝 → 처음에서 박자가 어긋난다.
     곡 끝 쪽에서 "여기서부터 들리는 소리가 곡 첫머리와 가장 닮은 지점 L"을 찾는다 (소리 세기가 올라가는 순간들 = 박자를
     10ms 단위로 잰 곡선끼리 비교 → 박자가 맞는 자리). 곡은 0~L 만 쓰고, 첫머리 XFADE_S 동안 L 뒤 소리를 줄이며 겹친다.
     그러면 L 직전 → (L 뒤 소리가 이어지다가) 첫머리로 자연스럽게 넘어간다. 박자 없는 경기장 소리는 끝을 그냥 겹친다.
  2) 크기: 곡마다 평소 크기(0.5초 RMS 중앙값)를 TARGET_DB 로 맞춘다 (옛 합성 곡과 비슷하게 → balance.json bgm 크기 수치가
     그대로 통한다). 최대 진폭이 PEAK 를 넘지 않게 덜 키운다.
  3) OGG Vorbis 로 압축 (곡당 2~3MB, WAV 의 약 1/8). Godot 가 OGG 루프를 이어 붙인다 (MusicPlayer).
심판 콜이 하는 일: 앞 무음을 자른다 (콜이 늦게 들리지 않게 — 실제로 원본 앞에 0.2초 무음이 있다), 최대 진폭 맞춤, 끝 살짝 줄임.
  짧은 소리라 압축하지 않고 16비트 모노 WAV.
에셋 출처는 licenses/assets.md (원칙 6).
"""
import os
import sys

import numpy as np
import soundfile as sf

ROOT = os.path.join(os.path.dirname(__file__), "..")
BGM = os.path.join(ROOT, "godot", "audio", "bgm")
SFX = os.path.join(ROOT, "godot", "audio", "sfx")

# (원본 이름 후보들, 종류, 게임 파일 이름)
FILES = [
    (["lobby.wav", "lobby.mp3"], "music", "lobby"),
    (["main.wav", "home.wav", "main.mp3", "home.mp3"], "music", "home"),
    (["crowd.wav", "game.wav", "crowd.mp3", "game.mp3"], "ambience", "game"),
    (["umpire-strike.mp3", "umpire_strike.mp3", "umpire_strike.wav"], "call", "call_strike"),
    (["umpire_strikeout.mp3", "umpire-strikeout.mp3", "umpire_strikeout.wav"], "call", "call_strikeout"),
    (["umpire_ball.mp3", "umpire-ball.mp3", "umpire_ball.wav"], "call", "call_ball"),
]

TARGET_DB = {"music": -17.0, "ambience": -24.0}  # 0.5초 RMS 중앙값 (옛 합성 곡 -17dB)
PEAK = 0.95
XFADE_S = {"music": 0.5, "ambience": 2.0}
SEARCH_S = 30.0   # 곡 끝에서 이만큼 안쪽까지 루프 지점을 찾는다
MATCH_S = 6.0     # 비교하는 길이 (L 뒤 6초 ↔ 첫머리 6초)
HOP_S = 0.01
OGG_LEVEL = 0.5   # Vorbis 압축 정도 0(최고 음질)~1. 0.5 ≈ 140~160kbps
CALL_PEAK = 0.9
CALL_PRE_S = 0.012  # 콜 시작 앞에 남기는 여유 (자음 앞부분이 잘리지 않게)


def rms_db(x, sr):
    win = sr // 2
    v = [np.sqrt(np.mean(x[i:i + win] ** 2)) for i in range(0, len(x) - win, win)]
    return 20 * np.log10(np.median(v) + 1e-9)


def onset_env(mono, sr):
    """소리 세기가 올라가는 정도를 10ms 마다 (박자 곡선)"""
    hop = int(sr * HOP_S)
    n = len(mono) // hop
    frames = mono[: n * hop].reshape(n, hop)
    e = np.log(np.sqrt((frames ** 2).mean(1)) + 1e-4)
    d = np.maximum(np.diff(e, prepend=e[0]), 0)
    return d - d.mean(), hop


def find_loop_end(mono, sr):
    env, hop = onset_env(mono, sr)
    m = int(MATCH_S / HOP_S)
    head = env[:m]
    head = head / (np.linalg.norm(head) + 1e-9)
    lo = max(m, int((len(mono) / sr - SEARCH_S) / HOP_S))
    hi = len(env) - m
    best, best_l = -2.0, hi
    for l in range(lo, hi):
        seg = env[l:l + m]
        c = float(seg @ head) / (np.linalg.norm(seg) + 1e-9)
        if c > best:
            best, best_l = c, l
    # 샘플 단위로 다듬기: hop 안에서 파형이 첫머리와 가장 닮은 자리
    base = best_l * hop
    w = 4096
    ref = mono[:w]
    cand = range(max(0, base - hop), min(len(mono) - w, base + hop))
    fine = max(cand, key=lambda s: float(mono[s:s + w] @ ref))
    return fine, best


def make_loop(x, sr, kind):
    f = int(XFADE_S[kind] * sr)
    if kind == "music":
        l, score = find_loop_end(x.mean(1), sr)
    else:
        l, score = len(x) - f, None
    l = min(l, len(x) - f)
    t = np.linspace(0, np.pi / 2, f)[:, None]
    y = x[:l].copy()
    y[:f] = x[:f] * np.sin(t) + x[l:l + f] * np.cos(t)  # 같은 힘 겹치기 (가운데서 소리가 꺼지지 않게)
    return y, l, score


def level(y, sr, target_db):
    gain_db = target_db - rms_db(y.mean(1), sr)
    gain = min(10 ** (gain_db / 20), PEAK / (np.abs(y).max() + 1e-9))
    return y * gain, 20 * np.log10(gain)


def do_music(src, kind, name):
    x, sr = sf.read(src, always_2d=True)
    y, l, score = make_loop(x, sr, kind)
    y, g = level(y, sr, TARGET_DB[kind])
    out = os.path.join(BGM, name + ".ogg")
    # 한 번에 통째로 쓰면 libsndfile 1.2 의 Vorbis 쓰기가 죽는다 (exit 139) → 조금씩 나눠 쓴다
    with sf.SoundFile(out, "w", sr, y.shape[1], format="OGG", subtype="VORBIS", compression_level=OGG_LEVEL) as f:
        for i in range(0, len(y), 4096):
            f.write(y[i:i + 4096].astype(np.float32))
    match = f", 박자 맞음 {score:.2f}" if score is not None else ""
    print(f"{name}: {len(x) / sr:.1f}초 → 루프 {l / sr:.2f}초{match}, 크기 {g:+.1f}dB, {os.path.getsize(out) / 1e6:.1f}MB")


def do_call(src, name):
    x, sr = sf.read(src, always_2d=True)
    m = x.mean(1)
    thr = np.abs(m).max() * 0.05
    start = max(0, int(np.argmax(np.abs(m) > thr)) - int(CALL_PRE_S * sr))
    end = len(m) - int(np.argmax(np.abs(m[::-1]) > thr * 0.2))
    y = m[start:min(len(m), end + int(0.05 * sr))]
    fade = min(len(y), int(0.03 * sr))
    y[-fade:] *= np.linspace(1, 0, fade)
    y = y * (CALL_PEAK / (np.abs(y).max() + 1e-9))
    out = os.path.join(SFX, name + ".wav")
    sf.write(out, y.astype(np.float32), sr, subtype="PCM_16")
    print(f"{name}: 앞 무음 {start / sr:.2f}초 자름 → {len(y) / sr:.2f}초")


def main():
    if len(sys.argv) < 2:
        sys.exit("원본 폴더를 주세요: scripts/import_audio.py ~/Desktop/audio")
    src_dir = os.path.expanduser(sys.argv[1])
    for names, kind, name in FILES:
        src = next((os.path.join(src_dir, n) for n in names if os.path.exists(os.path.join(src_dir, n))), None)
        if src is None:
            print(f"{name}: 원본 없음 ({' / '.join(names)}) — 건너뜀")
            continue
        if kind == "call":
            do_call(src, name)
        else:
            do_music(src, kind, name)
            old = os.path.join(BGM, name + ".wav")
            if os.path.exists(old):
                os.remove(old)  # 옛 합성 곡 (OGG 가 대신한다)
                if os.path.exists(old + ".import"):
                    os.remove(old + ".import")


if __name__ == "__main__":
    main()
