#!/usr/bin/env python3
"""배경음악(BGM) 3곡을 코드로 합성한다. 음원·샘플·AI 생성 없이 사인파·펄스파·잡음만 섞는다 (효과음 SoundSynth 와 같은 방식).

    python3 scripts/make_bgm.py          (외부 라이브러리 필요 없음)

나오는 것 (godot/audio/bgm/, 16비트 모노 WAV, 끝에서 처음으로 끊김 없이 이어지는 루프):
    lobby.wav   로비(타이틀·선수 만들기): 밝고 힘찬 오프닝 — C장조 120BPM 16마디
    home.wav    홈(내 방·스케줄·기록실 등): 느긋한 일상 — F장조 92BPM 16마디, 일렉 피아노 + 플루트
    game.wav    경기(경기·연습): 음악 없이 경기장 소리만 — 떼창 응원가·구호 + 막대풍선·응원북·박수 (140BPM 24마디)

곡을 고치려면 아래 SONGS 의 코드 진행·멜로디 문자열을 바꾸고 다시 실행한다.
멜로디 문자열: "음이름옥타브:박자" 를 띄어 쓴다 (예 "C5:1 E5:.5 r:.5" — r 은 쉼표, # 은 반음 올림, b 는 반음 내림).

끊김 없는 루프: 곡 길이만큼의 고리 버퍼에 더한다. 끝을 넘어간 음의 꼬리는 처음으로 돌아가 겹친다.
WAV 에 smpl(루프 지점) 덩어리도 넣어 Godot 가 루프로 읽는다 (MusicPlayer 도 루프를 한 번 더 걸어 둔다).
잡음은 고정 시드 난수 — 실행할 때마다 똑같은 파일이 나온다.
"""

import math
import os
import random
import struct
import wave

RATE = 32000
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "godot", "audio", "bgm")

NOTE_INDEX = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
CHORD_INTERVALS = {
    "": [0, 4, 7],
    "m": [0, 3, 7],
    "maj7": [0, 4, 7, 11],
    "m7": [0, 3, 7, 10],
    "7": [0, 4, 7, 10],
    "m6": [0, 3, 7, 9],
}


def midi_of(name: str) -> int:
    """'C#5' → 73"""
    letter = name[0]
    rest = name[1:]
    shift = 0
    while rest and rest[0] in "#b":
        shift += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + NOTE_INDEX[letter] + shift


def freq(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def parse_melody(text: str):
    """"C5:1 r:.5 …" → [(시작 박, 길이 박, midi)]"""
    out = []
    beat = 0.0
    for tok in text.split():
        name, dur = tok.split(":")
        d = float(dur)
        if name != "r":
            out.append((beat, d, midi_of(name)))
        beat += d
    return out, beat


def parse_chord(token: str):
    """'Em7' 'G/B' 'Bbmaj7:2' → (근음 midi(3옥타브), 화음 음정들, 베이스 근음, 박자)"""
    beats = None
    if ":" in token:
        token, b = token.split(":")
        beats = float(b)
    bass_name = None
    if "/" in token:
        token, bass_name = token.split("/")
    root = token[0]
    i = 1
    if i < len(token) and token[i] in "#b":
        i += 1
    quality = token[i:]
    root_midi = midi_of(token[:i] + "3")
    bass_midi = midi_of(bass_name + "2") if bass_name else root_midi - 12
    if bass_midi < 36:
        bass_midi += 12
    return root_midi, CHORD_INTERVALS[quality], bass_midi, beats


# ---------------------------------------------------------------- 고리 버퍼 믹서

class Mix:
    def __init__(self, bpm: float, bars: int):
        self.beat_s = 60.0 / bpm
        self.beats = bars * 4
        self.n = int(round(self.beats * self.beat_s * RATE))
        self.buf = [0.0] * self.n

    def at(self, beat: float) -> int:
        return int(round(beat * self.beat_s * RATE))

    def add(self, start_beat: float, samples, gain: float = 1.0):
        n = self.n
        buf = self.buf
        i0 = self.at(start_beat) % n
        for k, v in enumerate(samples):
            buf[(i0 + k) % n] += v * gain


# ---------------------------------------------------------------- 악기 (모두 샘플 목록을 돌려준다)

def env_adsr(n, a, d, s, r, sustain_n):
    """attack·decay·release 는 초. sustain_n 이후 release"""
    a_n = max(1, int(a * RATE))
    d_n = max(1, int(d * RATE))
    r_n = max(1, int(r * RATE))
    out = []
    for i in range(n):
        if i < a_n:
            e = i / a_n
        elif i < a_n + d_n:
            e = 1.0 - (1.0 - s) * (i - a_n) / d_n
        else:
            e = s
        if i >= sustain_n:
            e *= max(0.0, 1.0 - (i - sustain_n) / r_n)
        out.append(e)
    return out


def lowpass(samples, cutoff_hz):
    a = 1.0 - math.exp(-2 * math.pi * cutoff_hz / RATE)
    y = 0.0
    out = []
    for x in samples:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(samples, cutoff_hz):
    lp = lowpass(samples, cutoff_hz)
    return [x - l for x, l in zip(samples, lp)]


def pulse_lead(midi, dur_s, duty=0.25, vib=0.12, cutoff=3200, a=0.01, d=0.08, s=0.7, r=0.08):
    """펄스파 리드 (조금 깎아서 부드럽게). vib = 반음 단위 비브라토 깊이, 0.15초 뒤부터"""
    sustain_n = int(dur_s * RATE)
    n = sustain_n + int(r * RATE)
    env = env_adsr(n, a, d, s, r, sustain_n)
    f0 = freq(midi)
    phase = 0.0
    out = []
    for i in range(n):
        t = i / RATE
        v = vib * min(1.0, max(0.0, (t - 0.15) / 0.2)) * math.sin(2 * math.pi * 5.5 * t)
        f = f0 * 2 ** (v / 12)
        phase = (phase + f / RATE) % 1.0
        out.append((1.0 if phase < duty else -1.0) * env[i])
    return lowpass(out, cutoff)


def flute(midi, dur_s, rng):
    """사인 + 약한 2배음 + 숨소리, 느린 비브라토"""
    sustain_n = int(dur_s * RATE)
    r = 0.12
    n = sustain_n + int(r * RATE)
    env = env_adsr(n, 0.05, 0.1, 0.8, r, sustain_n)
    f0 = freq(midi)
    phase = 0.0
    breath = lowpass([rng.uniform(-1, 1) for _ in range(n)], 2500)
    out = []
    for i in range(n):
        t = i / RATE
        v = 0.15 * min(1.0, max(0.0, (t - 0.2) / 0.3)) * math.sin(2 * math.pi * 5.0 * t)
        phase = (phase + f0 * 2 ** (v / 12) / RATE) % 1.0
        x = math.sin(2 * math.pi * phase) + 0.18 * math.sin(4 * math.pi * phase) + 0.12 * breath[i]
        out.append(x * env[i])
    return out


def epiano(midi, dur_s):
    """일렉 피아노: 사인 몇 개, 높은 배음일수록 빨리 사라진다 + 살짝 흔들림"""
    sustain_n = int(dur_s * RATE)
    r = 0.25
    n = sustain_n + int(r * RATE)
    f0 = freq(midi)
    out = []
    for i in range(n):
        t = i / RATE
        rel = 1.0 if i < sustain_n else max(0.0, 1.0 - (i - sustain_n) / (r * RATE))
        atk = min(1.0, t / 0.004)
        x = (math.sin(2 * math.pi * f0 * t) * math.exp(-t * 1.6)
             + 0.35 * math.sin(2 * math.pi * 2 * f0 * t) * math.exp(-t * 4.0)
             + 0.12 * math.sin(2 * math.pi * 3 * f0 * t) * math.exp(-t * 8.0)
             + 0.08 * math.sin(2 * math.pi * 7.02 * f0 * t) * math.exp(-t * 30.0))
        x *= 1.0 + 0.12 * math.sin(2 * math.pi * 4.5 * t)
        out.append(x * atk * rel)
    return out


def pad(midis, dur_s, cutoff=1400):
    """패드: 음마다 살짝 어긋난 톱니파 두 개, 천천히 커지고 천천히 사라진다"""
    sustain_n = int(dur_s * RATE)
    r = 0.35
    n = sustain_n + int(r * RATE)
    env = env_adsr(n, 0.25, 0.3, 0.8, r, sustain_n)
    out = [0.0] * n
    for m in midis:
        for detune in (-0.08, 0.08):
            f = freq(m + detune)
            ph = (m * 0.137) % 1.0
            for i in range(n):
                ph = (ph + f / RATE) % 1.0
                out[i] += (2 * ph - 1) * env[i]
    scale = 1.0 / (2 * len(midis))
    return lowpass([x * scale for x in out], cutoff)


def brass(midis, dur_s):
    """브라스 짧게 '빠!': 톱니파에 여는 필터 (처음 밝고 금방 어두워진다)"""
    n = int((dur_s + 0.06) * RATE)
    sustain_n = int(dur_s * RATE)
    env = env_adsr(n, 0.008, 0.12, 0.55, 0.06, sustain_n)
    raw = [0.0] * n
    for m in midis:
        for detune in (-0.06, 0.06):
            f = freq(m + detune)
            ph = 0.0
            for i in range(n):
                ph = (ph + f / RATE) % 1.0
                raw[i] += 2 * ph - 1
    out = []
    y = 0.0
    for i in range(n):
        t = i / RATE
        cutoff = 900 + 3200 * math.exp(-t * 14)
        a = 1.0 - math.exp(-2 * math.pi * cutoff / RATE)
        y += a * (raw[i] / (2 * len(midis)) - y)
        out.append(y * env[i])
    return out


def bass(midi, dur_s, kind="tri"):
    """베이스: 삼각파(tri) 또는 둥근 펄스(pluck)"""
    sustain_n = int(dur_s * RATE * 0.92)
    r = 0.04
    n = sustain_n + int(r * RATE)
    env = env_adsr(n, 0.005, 0.15, 0.75, r, sustain_n)
    f = freq(midi)
    ph = 0.0
    out = []
    for i in range(n):
        ph = (ph + f / RATE) % 1.0
        if kind == "tri":
            x = 4 * abs(ph - 0.5) - 1
        else:
            x = (1.0 if ph < 0.5 else -1.0) * 0.6 + math.sin(2 * math.pi * ph) * 0.6
        out.append(x * env[i])
    return lowpass(out, 900 if kind == "tri" else 700)


# ---------------------------------------------------------------- 타악기

def kick(punch=1.0):
    n = int(0.28 * RATE)
    out = []
    ph = 0.0
    for i in range(n):
        t = i / RATE
        f = 48 + 110 * math.exp(-t * 32) * punch
        ph += f / RATE
        out.append(math.sin(2 * math.pi * ph) * math.exp(-t * 11))
    return out


def snare(rng, length=0.18, tone=0.5):
    n = int(length * RATE)
    noise = highpass([rng.uniform(-1, 1) for _ in range(n)], 1200)
    out = []
    for i in range(n):
        t = i / RATE
        out.append(noise[i] * math.exp(-t * 22) * 0.9 + tone * math.sin(2 * math.pi * 185 * t) * math.exp(-t * 30))
    return out


def clap(rng):
    n = int(0.2 * RATE)
    noise = lowpass(highpass([rng.uniform(-1, 1) for _ in range(n)], 900), 5000)
    out = []
    for i in range(n):
        t = i / RATE
        # 박수 여러 개가 조금씩 어긋나 겹친 소리: 짧은 꼭지 세 번 + 꼬리
        bursts = sum(math.exp(-max(0.0, t - d) * 140) if t >= d else 0.0 for d in (0.0, 0.011, 0.023))
        out.append(noise[i] * (0.6 * bursts + 0.5 * math.exp(-t * 18)))
    return out


def hat(rng, open_=False):
    length = 0.22 if open_ else 0.045
    n = int(length * RATE)
    noise = highpass([rng.uniform(-1, 1) for _ in range(n)], 6500)
    k = 9 if open_ else 70
    return [noise[i] * math.exp(-(i / RATE) * k) for i in range(n)]


def shaker(rng):
    n = int(0.09 * RATE)
    noise = highpass([rng.uniform(-1, 1) for _ in range(n)], 4500)
    return [noise[i] * math.sin(math.pi * i / n) ** 2 for i in range(n)]


def rim(rng):
    n = int(0.06 * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        out.append((math.sin(2 * math.pi * 1700 * t) * 0.6 + rng.uniform(-1, 1) * 0.4) * math.exp(-t * 90))
    return out


def crash(rng):
    n = int(1.6 * RATE)
    noise = highpass([rng.uniform(-1, 1) for _ in range(n)], 4000)
    return [noise[i] * math.exp(-(i / RATE) * 2.6) for i in range(n)]


# ---------------------------------------------------------------- 관중 (응원)

def bandpass(samples, f, q):
    """2차 대역 통과 필터 (RBJ). 목소리의 모음 공명(포먼트)을 만든다"""
    w = 2 * math.pi * f / RATE
    alpha = math.sin(w) / (2 * q)
    a0 = 1 + alpha
    b0, b2 = alpha / a0, -alpha / a0
    a1, a2 = -2 * math.cos(w) / a0, (1 - alpha) / a0
    x1 = x2 = y1 = y2 = 0.0
    out = []
    for x in samples:
        y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, x
        y2, y1 = y1, y
        out.append(y)
    return out


def loop_filter(buf, fn, prime_s=0.3):
    """고리 버퍼용 필터: 끝부분을 앞에 붙여 필터를 데운 뒤 잘라 낸다 (루프 이음매에서 튀지 않게)"""
    k = int(prime_s * RATE)
    return fn(buf[-k:] + buf)[k:]


## 모음별 포먼트 (주파수, 크기): 사람 목소리처럼 들리게 하는 공명
VOWELS = {
    "a": [(800, 1.0), (1150, 0.5), (2900, 0.12)],
    "o": [(450, 1.0), (800, 0.45), (2830, 0.08)],
    "e": [(420, 0.8), (1900, 0.55), (2550, 0.25)],
    # 소리 지르는 밝은 "아!": 입을 크게 벌려 F1 이 높고, 3·4번째 공명이 세다 (함성용)
    "A": [(900, 1.0), (1300, 0.75), (2800, 0.45), (3600, 0.3)],
}


def formant(buf, vowel, q=5.0):
    out = [0.0] * len(buf)
    for f, g in VOWELS[vowel]:
        for i, v in enumerate(bandpass(buf, f, q)):
            out[i] += v * g
    return out


def crowd_sing(mix, midi, start_beat, dur_s, rng, voices=8):
    """관중 여럿이 한 음을 따라 부른다 (모음은 나중에 formant 로 한 번에). 사람마다 음높이·박자·떨림이 조금씩 다르고,
    대부분 한 옥타브 아래(남자 목소리)로 부른다"""
    for v in range(voices):
        octave = -12 if v % 3 else 0
        f0 = freq(midi + octave + rng.gauss(0, 0.06))
        delay_beats = rng.uniform(0.0, 0.02) / mix.beat_s
        sustain_n = int(dur_s * RATE)
        n = sustain_n + int(0.07 * RATE)
        env = env_adsr(n, 0.035, 0.1, 0.8, 0.07, sustain_n)
        vib_rate, vib_ph, ph = rng.uniform(4.5, 6.5), rng.random(), rng.random()
        out = []
        for i in range(n):
            f = f0 * (1 + 0.004 * math.sin(2 * math.pi * (vib_rate * i / RATE + vib_ph)))
            ph = (ph + f / RATE) % 1.0
            out.append((2 * ph - 1) * env[i])
        mix.add(start_beat + delay_beats, out, 1.0 / voices)


def crowd_shout(mix, start_beat, rng, voices=12, length=0.17):
    """관중 구호 한 마디 "헤이!": 저마다 다른 높이에서 내려꽂는 짧은 외침 + 거친 숨소리 (모음은 나중에)"""
    for v in range(voices):
        f0 = rng.uniform(130, 330)
        delay_beats = rng.uniform(0.0, 0.03) / mix.beat_s
        n = int(length * RATE)
        ph = rng.random()
        out = []
        for i in range(n):
            t = i / RATE
            ph = (ph + f0 * (1.0 - 0.18 * t / length) / RATE) % 1.0
            e = min(1.0, t / 0.012) * math.exp(-t * 9)
            out.append(((2 * ph - 1) * 0.9 + rng.uniform(-1, 1) * 0.2) * e)
        mix.add(start_beat + delay_beats, out, 1.0 / voices)


def thundersticks(rng, people=8):
    """막대풍선 '탁!': 여러 사람이 조금씩 어긋나게 두드리는 플라스틱 막대 소리"""
    n = int(0.1 * RATE)
    out = [0.0] * n
    for _ in range(people):
        d = int(rng.uniform(0, 0.014) * RATE)
        f = rng.uniform(800, 1500)
        for i in range(n - d):
            t = i / RATE
            out[i + d] += (rng.uniform(-1, 1) * 0.7 + math.sin(2 * math.pi * f * t) * 0.5) * math.exp(-t * 60)
    return lowpass(highpass([x / people for x in out], 450), 6500)


def cheer_drum():
    """응원북 '둥': 크고 둥근 북. 낮게 떨어지는 음 + 긴 울림"""
    n = int(0.55 * RATE)
    out = []
    ph = 0.0
    for i in range(n):
        t = i / RATE
        ph += (62 + 45 * math.exp(-t * 18)) / RATE
        out.append(math.sin(2 * math.pi * ph) * math.exp(-t * 6) + 0.25 * math.sin(4 * math.pi * ph) * math.exp(-t * 14))
    return out


def trumpet(midi, dur_s):
    """응원 트럼펫: 톱니파에 여닫는 필터 (불 때 밝게 터지고 이어서 조금 어두워진다) + 길면 비브라토"""
    sustain_n = int(dur_s * RATE)
    n = sustain_n + int(0.05 * RATE)
    env = env_adsr(n, 0.012, 0.09, 0.75, 0.05, sustain_n)
    f0 = freq(midi)
    ph = ph2 = 0.0
    y = 0.0
    out = []
    for i in range(n):
        t = i / RATE
        vib = 0.12 * min(1.0, max(0.0, (t - 0.18) / 0.15)) * math.sin(2 * math.pi * 5.8 * t)
        f = f0 * 2 ** (vib / 12)
        ph = (ph + f / RATE) % 1.0
        ph2 = (ph2 + f * 1.004 / RATE) % 1.0
        raw = (2 * ph - 1) + 0.6 * (2 * ph2 - 1)
        cutoff = 1300 + 3400 * math.exp(-t * 10) + 900 * env[i]
        y += (1.0 - math.exp(-2 * math.pi * cutoff / RATE)) * (raw - y)
        out.append(y * env[i])
    return out


def active_rms(buf):
    """소리가 나는 구간만의 RMS (드문드문한 파트도 크기를 맞출 수 있게)"""
    vals = [x * x for x in buf if abs(x) > 1e-4]
    return math.sqrt(sum(vals) / len(vals)) if vals else 1.0


# ---------------------------------------------------------------- 관중 함성 (효과음)

SFX_DIR = os.path.join(os.path.dirname(__file__), "..", "godot", "audio", "sfx")


def cheer(name, length, voices, spread, rng, whistles=0, claps=0, pitch=1.0):
    """관중 함성 "와~!": 목소리 voices 명이 spread 초 안에 저마다 터져 나온다. 사람마다 "오"로 시작해 0.04~0.12초 사이에
    입을 크게 벌린 밝은 "아"(VOWELS["A"])로 열린다 = "와". 음은 처음에 올렸다가 끝까지 유지한다 (끝이 처지면 "이유~"처럼 들린다).
    소리 지르는 목소리라 높다 (남 170~280Hz, 여 300~520Hz, × pitch).
    휘파람·박수를 조금 섞고 경기장 반사음을 입힌다. 300Hz 아래는 깎는다 (낮은 소리가 많으면 '쾅' 하는 사고 소리처럼 들린다).
    루프가 아니라 한 번 울리는 소리"""
    n = int(length * RATE)
    src = {"o": [0.0] * n, "A": [0.0] * n}
    for _ in range(voices):
        start = int(rng.uniform(0, spread) * RATE)
        dur = rng.uniform(0.45, 0.95) * (length - start / RATE)
        f0 = (rng.uniform(170, 280) if rng.random() < 0.55 else rng.uniform(300, 520)) * pitch
        m = int(dur * RATE)
        ph = rng.random()
        gain = rng.uniform(0.4, 1.0)
        for i in range(m):
            if start + i >= n:
                break
            t = i / RATE
            rise = 1.0 + 0.2 * min(1.0, t / 0.12)  # "와↗" 올리고 유지
            ph = (ph + f0 * rise / RATE) % 1.0
            e = min(1.0, t / 0.03) * min(1.0, (m - i) / (0.3 * RATE))
            x = (2 * ph - 1) * e * gain
            w_o = min(1.0, max(0.0, (0.12 - t) / 0.08))  # 0.04초까지 "오", 0.12초부터 "아"
            src["o"][start + i] += x * w_o
            src["A"][start + i] += x * (1.0 - w_o)
    out = [0.0] * n
    for v, buf in src.items():
        for i, x in enumerate(formant(buf, v)):
            out[i] += x
    out = highpass(highpass(out, 300), 300)
    # 높은 대역을 조금 띄워 밝게: 1.5kHz 위 × 1.5 (밝기는 주로 "아"의 높은 공명에서 온다. 3kHz 위 치찰음을 너무 띄우면 "이"처럼 들린다)
    bright = highpass(highpass(out, 1500), 1500)
    out = [x + 1.5 * b for x, b in zip(out, bright)]
    g0 = 0.15 / active_rms(out)
    out = [x * g0 for x in out]
    # 숨소리: 목소리 대역 잡음을 전체 크기에 맞춰 조금
    env = lowpass([abs(x) for x in out], 8)
    breath = highpass(formant([rng.uniform(-1, 1) for _ in range(n)], "A", 1.5), 600)
    peak_env = max(env) or 1.0
    g = 0.25 * active_rms(out) / active_rms(breath)
    for i in range(n):
        out[i] += breath[i] * env[i] / peak_env * g
    # 휘파람: 높게 올렸다 내리는 "휘익"
    for _ in range(whistles):
        start = int(rng.uniform(0.2, length * 0.5) * RATE)
        m = int(rng.uniform(0.4, 0.8) * RATE)
        f0 = rng.uniform(2200, 3000)
        ph = 0.0
        for i in range(m):
            if start + i >= n:
                break
            u = i / m
            ph += f0 * (1 + 0.25 * math.sin(math.pi * u)) / RATE
            out[start + i] += math.sin(2 * math.pi * ph) * math.sin(math.pi * u) * 0.08
    # 박수: 여기저기서
    for _ in range(claps):
        c = clap(rng)
        start = int(rng.uniform(0.3, length * 0.8) * RATE)
        g = rng.uniform(0.02, 0.06)
        for i, x in enumerate(highpass(c, 800)):
            if start + i < n:
                out[start + i] += x * g
    # 경기장 반사음
    dry = out[:]
    echo = lowpass(dry, 5000)
    for delay, g in ((0.09, 0.2), (0.17, 0.12), (0.29, 0.06)):
        d = int(delay * RATE)
        for i in range(n - d):
            out[i + d] += echo[i] * g
    # 끝을 부드럽게 줄이고 저장
    fade = int(0.4 * RATE)
    for i in range(fade):
        out[n - fade + i] *= 1 - i / fade
    peak = max(abs(x) for x in out) or 1.0
    os.makedirs(SFX_DIR, exist_ok=True)
    path = os.path.join(SFX_DIR, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(x / peak * 0.9 * 32767)) for x in out))
    print("%s: %.1f초, %d KB" % (path, length, os.path.getsize(path) // 1024))


def sfx_cheers():
    rng = random.Random(4)
    # 안타·볼넷: 짧고 높게 "와~!" (pitch 1.4 → 남 240~390Hz·여 420~730Hz)
    cheer("cheer_small", 2.2, 45, 0.25, rng, whistles=3, claps=6, pitch=1.4)
    cheer("cheer_big", 4.5, 90, 0.35, rng, whistles=6, claps=20, pitch=1.2)   # 홈런: 크게 오래


# ---------------------------------------------------------------- 심판 콜 (목소리 합성)
#
# 사람 목소리 = 성대 떨림(톱니 모양 펄스) → 입·목구멍이 만드는 공명 4개(포먼트)를 차례로 통과.
# 공명 주파수를 시간에 따라 옮기면 모음이 바뀐다 ("아" F1 800·F2 1250 → "이" F1 320·F2 2200).
# 자음은 잡음으로: "스"는 높은 쉿 소리, "트·크"는 막았다 터뜨리는 짧은 잡음(파열), 그 사이는 무음.
# 키프레임(시각마다 공명·크기·음높이)을 선형으로 이어 한 샘플씩 만든다 (Klatt 합성기의 단순판).

def klatt_resonator(f, bw):
    """2극 공명기 계수 (A, B, C): y = A·x + B·y1 + C·y2"""
    t = 1.0 / RATE
    c = -math.exp(-2 * math.pi * bw * t)
    b = 2 * math.exp(-math.pi * bw * t) * math.cos(2 * math.pi * f * t)
    return 1 - b - c, b, c


def interp(keys, t):
    """keys = [(시각, 값 또는 튜플)] 을 t 에서 선형 보간"""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            u = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            if isinstance(v0, tuple):
                return tuple(a + (b - a) * u for a, b in zip(v0, v1))
            return v0 + (v1 - v0) * u
    return keys[-1][1]


def speak(length, formants, voice_amp, f0, noise, rng, bandwidths=(90, 110, 170, 250)):
    """formants: [(t, (F1, F2, F3, F4))], voice_amp: [(t, 크기)], f0: [(t, Hz)],
    noise: [(t, (크기, 가운데 주파수))] — 자음 잡음 (공명을 거치지 않고 대역 통과만)"""
    n = int(length * RATE)
    ph = 0.0
    ys = [[0.0, 0.0] for _ in range(4)]
    nz = [0.0, 0.0, 0.0, 0.0]  # 잡음 대역 통과 상태 x1 x2 y1 y2
    out = []
    jitter = 0.0
    for i in range(n):
        t = i / RATE
        fs = interp(formants, t)
        amp = interp(voice_amp, t)
        if i % 64 == 0:
            jitter = rng.gauss(0, 0.008)  # 성대 떨림의 미세한 흔들림 (기계음 티를 줄인다)
        f = interp(f0, t) * (1 + jitter)
        ph = (ph + f / RATE) % 1.0
        # 성대 펄스: 톱니에 가깝지만 닫히는 쪽이 빠르다 + 숨소리 조금
        src = (2 * ph - 1) ** 3 * 0.6 + (2 * ph - 1) * 0.4 + rng.uniform(-1, 1) * 0.06
        x = src * amp
        # 공명 4개를 차례로 (cascade)
        for k in range(4):
            a, b, c = klatt_resonator(fs[k], bandwidths[k])
            y = a * x + b * ys[k][0] + c * ys[k][1]
            ys[k][1] = ys[k][0]
            ys[k][0] = y
            x = y
        # 자음 잡음
        namp, nf = interp(noise, t)
        w = 2 * math.pi * nf / RATE
        al = math.sin(w) / (2 * 1.2)
        a0 = 1 + al
        xn = rng.uniform(-1, 1)
        yn = (al * xn - al * nz[1] + 2 * math.cos(w) * nz[2] - (1 - al) * nz[3]) / a0
        nz[1], nz[0] = nz[0], xn
        nz[3], nz[2] = nz[2], yn
        out.append(x + yn * namp * 6.0)
    return out


def shout_finish(name, samples, echo_gain=0.25):
    """외치는 소리로 다듬기: 살짝 찌그러뜨려(tanh) 힘을 주고, 낮은 웅웅거림을 깎고, 경기장 반사음을 입혀 저장"""
    peak = max(abs(x) for x in samples) or 1.0
    out = [math.tanh(x / peak * 1.8) for x in samples]
    out = highpass(out, 120)
    pad = int(0.6 * RATE)
    out = out + [0.0] * pad
    dry = out[:]
    echo = lowpass(dry, 4000)
    for delay, g in ((0.085, 0.3), (0.16, 0.18), (0.27, 0.1), (0.41, 0.05)):
        d = int(delay * RATE)
        for i in range(len(out) - d):
            out[i + d] += echo[i] * g * echo_gain / 0.25
    peak = max(abs(x) for x in out) or 1.0
    os.makedirs(SFX_DIR, exist_ok=True)
    path = os.path.join(SFX_DIR, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(x / peak * 0.92 * 32767)) for x in out))
    print("%s: %.2f초" % (path, len(out) / RATE))


# 모음 공명 (남자 목소리, 외칠 때라 F1 이 조금 높다)
V_A = (820, 1250, 2550, 3500)
V_I = (330, 2150, 2900, 3600)
V_U = (340, 780, 2350, 3400)
V_R = (380, 1350, 1700, 3300)  # 혀를 튕기는 "ㄹ"
V_S = (400, 1600, 2600, 3500)  # 자음 사이 (입 모양만)


def call_strike(rng):
    """"스트라이크!" — 한국 심판식으로 '스↗트라이↘크!' : '라'에서 음을 올려 힘을 주고 '이'에서 꺾는다"""
    formants = [(0.0, V_S), (0.14, V_S), (0.155, V_R), (0.18, V_A), (0.31, V_A), (0.37, V_I), (0.6, V_I)]
    voice = [(0.0, 0.0), (0.145, 0.0), (0.16, 0.7), (0.19, 1.0), (0.33, 1.0), (0.40, 0.8), (0.425, 0.0), (0.6, 0.0)]
    f0 = [(0.0, 170.0), (0.15, 175.0), (0.22, 235.0), (0.31, 225.0), (0.42, 175.0), (0.6, 170.0)]
    noise = [
        (0.0, (0.0, 6500.0)), (0.01, (0.25, 6500.0)), (0.08, (0.3, 6000.0)), (0.095, (0.0, 6000.0)),  # 스 (작게 — 크면 쉿 소리만 들린다)
        (0.125, (0.0, 4000.0)), (0.13, (1.0, 4000.0)), (0.145, (0.0, 4000.0)),                       # 트 (막았다 터뜨림)
        (0.465, (0.0, 2200.0)), (0.47, (0.9, 2200.0)), (0.49, (0.25, 2600.0)), (0.55, (0.0, 2600.0)),  # 크 + 숨
        (0.6, (0.0, 2600.0)),
    ]
    shout_finish("call_strike", speak(0.6, formants, voice, f0, noise, rng))


def call_strikeout(rng):
    """"스트라익~ 아웃!" — 루킹 삼진 세리머니: 스트라익을 짧게 끊고 한 박 쉰 뒤 '아웃!'을 높고 크게"""
    formants = [(0.0, V_S), (0.14, V_S), (0.155, V_R), (0.18, V_A), (0.29, V_A), (0.34, V_I), (0.45, V_I),
                (0.52, V_A), (0.62, V_A), (0.72, V_U), (0.95, V_U)]
    voice = [(0.0, 0.0), (0.145, 0.0), (0.16, 0.7), (0.19, 1.0), (0.30, 1.0), (0.37, 0.7), (0.39, 0.0),
             (0.51, 0.0), (0.53, 1.0), (0.66, 1.0), (0.76, 0.8), (0.79, 0.0), (0.95, 0.0)]
    f0 = [(0.0, 170.0), (0.15, 175.0), (0.22, 230.0), (0.30, 220.0), (0.38, 180.0),
          (0.52, 250.0), (0.58, 270.0), (0.68, 245.0), (0.79, 190.0), (0.95, 180.0)]
    noise = [
        (0.0, (0.0, 6500.0)), (0.01, (0.25, 6500.0)), (0.08, (0.3, 6000.0)), (0.095, (0.0, 6000.0)),  # 스 (작게 — 크면 쉿 소리만 들린다)
        (0.125, (0.0, 4000.0)), (0.13, (1.0, 4000.0)), (0.145, (0.0, 4000.0)),                       # 트
        (0.415, (0.0, 2200.0)), (0.42, (0.8, 2200.0)), (0.44, (0.0, 2200.0)),                        # 익(ㄱ)
        (0.505, (0.0, 1500.0)), (0.51, (0.15, 1500.0)), (0.53, (0.0, 1500.0)),                       # 아 (성문 터짐)
        (0.835, (0.0, 4200.0)), (0.84, (0.5, 4200.0)), (0.86, (0.0, 4200.0)),                        # 웃(ㅅ 받침, 약하게)
        (0.95, (0.0, 4200.0)),
    ]
    shout_finish("call_strikeout", speak(0.95, formants, voice, f0, noise, rng), 0.35)


def sfx_calls():
    rng = random.Random(5)
    call_strike(rng)
    call_strikeout(rng)


# ---------------------------------------------------------------- 곡 조립 도우미

def chord_timeline(bars):
    """마디별 코드 문자열 목록 → [(시작 박, 박자, 근음, 음정들, 베이스)]. 한 마디에 코드 둘이면 'Em7:2 A7:2'"""
    out = []
    beat = 0.0
    for bar in bars:
        toks = bar.split()
        default = 4.0 / len(toks)
        for tok in toks:
            root, iv, bass_m, beats = parse_chord(tok)
            b = beats if beats is not None else default
            out.append((beat, b, root, iv, bass_m))
            beat += b
    return out


def voicing(root, iv, low=55, high=72):
    """화음을 low~high 사이로 모은다"""
    notes = []
    for x in iv:
        m = root + x
        while m < low:
            m += 12
        while m > high:
            m -= 12
        notes.append(m)
    return sorted(notes)


def put_melody(mix, text, synth, gain, offset_beats=0.0):
    notes, total = parse_melody(text)
    assert abs(total - mix.beats) < 1e-6, "멜로디 길이 %.2f 박, 곡은 %d 박" % (total, mix.beats)
    for beat, dur, m in notes:
        mix.add(beat + offset_beats, synth(m, dur * mix.beat_s), gain)


def finish(mix, name, target_rms=0.16):
    """RMS 를 맞추고 부드럽게 눌러(tanh) 16비트 WAV + smpl 루프로 저장"""
    buf = mix.buf
    rms = math.sqrt(sum(x * x for x in buf) / len(buf)) or 1.0
    g = target_rms / rms
    data = bytearray()
    peak = 0.0
    for x in buf:
        y = math.tanh(x * g * 1.1) / 1.1
        peak = max(peak, abs(y))
        data += struct.pack("<h", int(max(-1.0, min(1.0, y)) * 32767))
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(bytes(data))
    _append_smpl(path, mix.n)
    print("%s: %.1f초, 피크 %.2f, %d KB" % (path, mix.n / RATE, peak, os.path.getsize(path) // 1024))


def _append_smpl(path, frames):
    """WAV 끝에 smpl 덩어리(루프: 0 → 마지막 샘플)를 붙이고 RIFF 크기를 고친다"""
    smpl = struct.pack("<9I", 0, 0, int(1e9 / RATE), 60, 0, 0, 0, 1, 0)
    smpl += struct.pack("<6I", 0, 0, 0, frames - 1, 0, 0)
    chunk = b"smpl" + struct.pack("<I", len(smpl)) + smpl
    with open(path, "r+b") as f:
        f.seek(0, 2)
        f.write(chunk)
        size = f.tell()
        f.seek(4)
        f.write(struct.pack("<I", size - 8))


# ---------------------------------------------------------------- 곡

def song_lobby():
    """로비: 밝고 힘찬 오프닝. 1~8마디 주제, 9~16마디 올라가서 다시 주제로"""
    rng = random.Random(1)
    mix = Mix(120, 16)
    bars = ["C", "G/B", "Am", "F", "C", "G", "F", "G",
            "F", "G", "Em", "Am", "Dm", "G", "C", "G"]
    melody = (
        "G4:1 C5:1 E5:1.5 D5:.5  D5:1 B4:1 G4:2  A4:1 C5:1 E5:1 A5:1  A5:1.5 G5:.5 F5:1 C5:1 "
        "E5:1 G5:1 C6:1.5 B5:.5  B5:1 G5:1 D5:2  C5:1 F5:1 A5:1 G5:.5 F5:.5  G5:3 r:1 "
        "A5:1.5 G5:.5 F5:1 E5:1  D5:1.5 E5:.5 F5:1 G5:1  G5:1.5 E5:.5 B4:1 E5:1  C5:1 E5:1 A5:2 "
        "F5:1.5 E5:.5 D5:1 A5:1  G5:1 F5:1 D5:1 B4:1  C5:1 E5:1 G5:1 C6:1  B5:1 A5:.5 G5:.5 D5:1 G4:1"
    )
    put_melody(mix, melody, lambda m, d: pulse_lead(m, d * 0.95, duty=0.25), 0.22)
    # 한 옥타브 아래 부드러운 겹침 (두께)
    put_melody(mix, melody, lambda m, d: pulse_lead(m - 12, d * 0.95, duty=0.5, vib=0.0, cutoff=1800), 0.08)
    for beat, b, root, iv, bass_m in chord_timeline(bars):
        mix.add(beat, pad(voicing(root, iv), b * mix.beat_s), 0.22)
        # 베이스: 8분음표로 근음·옥타브 번갈아
        for k in range(int(b * 2)):
            m = bass_m + (12 if k % 2 else 0)
            mix.add(beat + k * 0.5, bass(m, 0.5 * mix.beat_s), 0.32)
    for bar in range(16):
        b0 = bar * 4
        for k in (0, 2, 2.5):
            mix.add(b0 + k, kick(), 0.55)
        for k in (1, 3):
            mix.add(b0 + k, snare(rng), 0.32)
        for k in range(8):
            mix.add(b0 + k * 0.5, hat(rng), 0.10 if k % 2 else 0.14)
        if bar in (0, 8):
            mix.add(b0, crash(rng), 0.16)
        if bar in (7, 15):  # 마디 끝 필인
            for k in (3.25, 3.5, 3.75):
                mix.add(b0 + k, snare(rng, 0.12), 0.24)
    finish(mix, "lobby")


def song_home():
    """홈: 느긋한 일상. 일렉 피아노 반주 + 플루트 멜로디 + 가벼운 드럼"""
    rng = random.Random(2)
    mix = Mix(92, 16)
    bars = ["Fmaj7", "Em7 A7", "Dm7", "Cm7 F7", "Bbmaj7", "Am7", "Gm7", "C7",
            "Fmaj7", "Em7 A7", "Dm7", "Cm7 F7", "Bbmaj7", "Bbm6", "Am7 D7", "Gm7 C7"]
    melody = (
        "r:1 A4:.5 C5:.5 E5:2  D5:1 E5:1 C#5:1 A4:1  F5:1.5 E5:.5 D5:1 C5:1  Eb5:1 D5:1 C5:1 A4:1 "
        "D5:2 F5:1 A5:1  G5:1.5 E5:.5 C5:2  Bb4:1 D5:1 F5:1 E5:1  E5:3 r:1 "
        "r:1 C5:.5 F5:.5 A5:2  G5:1 E5:1 G5:.5 E5:.5 C#5:1  D5:2 A4:1 C5:1  Bb4:1 G4:1 A4:1 C5:1 "
        "D5:1 F5:1 A5:1.5 G5:.5  F5:1 Db5:1 Bb4:2  C5:1 E5:1 F#5:1 C5:1  Bb4:1 A4:1 G4:1 E4:1"
    )
    put_melody(mix, melody, lambda m, d: flute(m, d * 0.92, rng), 0.24)
    for beat, b, root, iv, bass_m in chord_timeline(bars):
        notes = voicing(root, iv, 53, 70)
        # 반주: 첫 박 길게, 2.5박째 짧게 (마디에 코드 둘이면 박마다 한 번)
        hits = [(0.0, 1.4), (1.5, 0.9)] if b >= 4 else [(0.0, 0.9), (1.5, 0.4)]
        for off, length in hits:
            if off < b:
                for m in notes:
                    mix.add(beat + off, epiano(m, length * mix.beat_s), 0.09)
        # 베이스: 근음 → 5도
        mix.add(beat, bass(bass_m, (b / 2 + 0.5) * mix.beat_s, "pluck"), 0.34)
        mix.add(beat + b * 0.75, bass(bass_m + 7, b * 0.25 * mix.beat_s, "pluck"), 0.26)
    for bar in range(16):
        b0 = bar * 4
        mix.add(b0, kick(0.6), 0.42)
        mix.add(b0 + 2.5, kick(0.6), 0.3)
        for k in (1, 3):
            mix.add(b0 + k, rim(rng), 0.16)
        for k in range(8):
            # 셔플: 뒷박을 조금 늦게
            mix.add(b0 + k * 0.5 + (0.08 if k % 2 else 0.0), shaker(rng), 0.06 if k % 2 else 0.09)
    finish(mix, "home", 0.13)


def song_game():
    """경기장 소리 (음악 없음): 관중이 응원가를 떼창한다.
    1~16마디 응원가 (8·16마디는 구호 "헤이! 헤이! 헤이헤이헤이!"), 17~24마디는 막대풍선 박수 리듬만.
    악기는 관중이 직접 내는 소리뿐 — 막대풍선·응원북·박수"""
    rng = random.Random(3)
    bpm, n_bars, song_bars = 140, 24, 16
    melody = (
        "G4:.5 G4:.5 B4:.5 D5:.5 r:.5 D5:.5 B4:1  G4:.5 G4:.5 B4:.5 D5:.5 G5:1 r:1 "
        "E5:.5 E5:.5 E5:.5 D5:.5 C5:1 E5:1  D5:.5 D5:.5 F#5:.5 A5:.5 D5:1 r:1 "
        "B4:.5 B4:.5 E5:.5 G5:.5 r:.5 G5:.5 E5:1  C5:.5 C5:.5 E5:.5 G5:.5 C6:1 r:1 "
        "A5:.5 A5:.5 F#5:.5 D5:.5 A5:.5 F#5:.5 D5:1  D5:.5 r:.5 D5:.5 r:.5 D5:.5 D5:.5 D5:.5 r:.5 "
        "B5:1 A5:.5 G5:.5 D5:1 G5:1  F#5:1 D5:.5 B4:.5 F#5:1 D5:1 "
        "E5:.5 G5:.5 C6:1 B5:.5 A5:.5 G5:1  A5:1.5 F#5:.5 D5:1 A4:1 "
        "G5:.5 G5:.5 G5:.5 F#5:.5 E5:1 B4:1  C5:.5 E5:.5 G5:.5 C6:.5 B5:1 G5:1 "
        "A5:1 E5:1 F#5:1 A5:1  G5:.5 r:.5 G5:.5 r:.5 G5:.5 G5:.5 G5:.5 r:.5"
    )
    notes, _ = parse_melody(melody)
    chant_bars = (7, 15)  # 0부터 센 마디: 구호 마디
    # 가사 대신 모음 순서: 음마다 돌아가며 붙여 "가사를 부르는" 느낌을 낸다
    lyric = "aoaaoaa" "oaaoaoa"

    def stem():
        return Mix(bpm, n_bars)

    sing = {v: stem() for v in ("a", "o")}
    shout_src = stem()
    sticks_m, drum_m, claps = stem(), stem(), stem()
    beat_s = shout_src.beat_s
    for idx, (beat, dur, m) in enumerate(notes):
        if int(beat // 4) in chant_bars:
            crowd_shout(shout_src, beat, rng, voices=16)
            continue
        vowel = lyric[idx % len(lyric)]
        crowd_sing(sing[vowel], m, beat, dur * beat_s * 0.88, rng, voices=14)

    # 목소리: 모음마다 공명을 입힌다 (끝부분으로 필터를 데워 루프 이음매가 없다)
    choir = [0.0] * shout_src.n
    for v, mix in sing.items():
        for i, x in enumerate(loop_filter(mix.buf, lambda b, v=v: formant(b, v))):
            choir[i] += x
    shouts = loop_filter(shout_src.buf, lambda b: formant(b, "e", 4.0))

    # 막대풍선: 응원가 동안 8분음표 '탁탁', 구호 마디는 구호에 맞춰 세게,
    # 응원가 뒤 8마디는 "탁탁 탁탁탁" 박수 리듬만
    stick_hits = [thundersticks(rng, 12) for _ in range(6)]
    for bar in range(n_bars):
        b0 = bar * 4
        if bar in chant_bars:
            for beat, dur, m in notes:
                if b0 <= beat < b0 + 4:
                    sticks_m.add(beat, stick_hits[rng.randrange(6)], 1.3)
        elif bar < song_bars:
            for k in range(8):
                sticks_m.add(b0 + k * 0.5, stick_hits[rng.randrange(6)], 1.0 if k % 2 == 0 else 0.7)
        elif bar % 2 == 0:
            for k in (0, 1, 2, 2.5, 3):
                sticks_m.add(b0 + k, stick_hits[rng.randrange(6)], 0.8)

    # 응원북: 응원가 동안 박마다 '둥', 구호 마디 끝은 '둥둥둥'. 응원가 뒤에는 쉰다
    for bar in range(song_bars):
        b0 = bar * 4
        for k in (0, 1, 2, 3):
            drum_m.add(b0 + k, cheer_drum(), 1.0 if k % 2 == 0 else 0.75)
        if bar in chant_bars:
            for k in (2.5, 3.5):
                drum_m.add(b0 + k, cheer_drum(), 0.8)

    # 관중 박수: 응원가 동안 2·4박
    for bar in range(song_bars):
        if bar not in chant_bars:
            for k in (1, 3):
                claps.add(bar * 4 + k, clap(rng), 1.0)

    # 파트별 크기 (소리 나는 구간 기준 RMS). 떼창이 앞, 막대풍선·북이 박. 웅성거림·바닥 소음은 없다 (2026-10-31 유저 "웅성거리는 잡음은 빼고")
    levels = [
        (choir, 0.18),
        (shouts, 0.18),
        (sticks_m.buf, 0.07),
        (drum_m.buf, 0.12),
        (claps.buf, 0.05),
    ]
    out = stem()
    for buf, target in levels:
        g = target / active_rms(buf)
        for i, x in enumerate(buf):
            out.buf[i] += x * g
    finish(out, "game", 0.15)


if __name__ == "__main__":
    import sys
    only = sys.argv[1:]  # 곡 이름을 주면 그 곡만 (예: python3 scripts/make_bgm.py game)
    for name, fn in (("lobby", song_lobby), ("home", song_home), ("game", song_game), ("cheer", sfx_cheers), ("call", sfx_calls)):
        if not only or name in only:
            fn()
