#!/usr/bin/env python3
"""컷신 도트 (2026-11-05): 이야기 카드 사이에 끼는 짧은 "애니메이션 영화" 장면의 그림을 코드로 찍어 만든다.
AI 생성 그림을 쓰지 않는다. 테두리·그늘 규칙과 색·인물 뼈대는 make_pixel_art.py · make_training_art.py 와 같다.

게임 화면(중계 카메라)을 그대로 쓰지 않는다 — 컷마다 따로 그린 판(배경)에 카메라를 대고, 인물 칸을 갈아 끼운다.
화면 4배 (그림 211×98 = 화면 844×392). 판은 카메라가 좌우로 움직일 만큼 넓게 그린다.

첫 컷신: EP05 김성호의 마지막 타석 (ep05_seongho)
    판 (cutscene_bg.png, 줄 = 판, 높이 98):
        dugout    덕아웃 안에서 운동장을 본다 (어두운 지붕·난간 + 해 질 녘 밝은 운동장) — 앉은 등들, 끝에 감독
        dark      얼굴 클로즈업 뒤 (덕아웃 안, 흐린 빛망울)
        plate     홈플레이트 옆모습 (노을 하늘·관중석 실루엣·흙·포수)
        baseline  1루 가는 길 옆모습 (480 넓이 — 카메라가 달리는 사람을 따라 흐른다)
        bag       1루 베이스 바로 옆, 땅 높이
        sky       루심 뒤 하늘
        wide      운동장 전체 (노을)
    인물 칸 (cutscene.png, 칸 96×96, 발 자리 FOOT, 줄 = 인물 동작, 왼쪽부터 장):
        coach     감독 서 있기 · 고개 돌림 (왼쪽을 본다)
        bench     덕아웃 앞줄 등 (실루엣): 앉음 · 일어나 난간에 붙음
        face      김성호 얼굴: 고개 숙임 · 눈 듦 · 이를 문다
        batter    김성호 타석 (오른쪽을 본다, 헬멧): 준비 · 내딛기 · 맞는 순간 · 팔로스루
        run       달리기 4장 (헬멧) · 달리기 4장 (헬멧 벗겨짐)
        helmet    날아가는 헬멧 3장 (돌리지 않고 장마다 따로 찍는다)
        foot      베이스를 밟는 발 (크게)
        mitt      1루수 미트 (크게) · 공이 꽂힌 미트
        ump       루심: 지켜봄 · 아웃 (주먹)
        tiny      먼 김성호: 서기 · 덕아웃 쪽으로 돌아섬 · 인사
        tiny_helmet  흙 위에 떨어진 헬멧 (작게)

    python3 scripts/make_cutscene_art.py   (pillow·numpy 필요)

나오는 것: godot/art/pixel/cutscene_bg.png, godot/art/pixel/cutscene.png, godot/scripts/characters/cutscene_meta.gd
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from make_pixel_art import PAL, Cell, capsule, circle, ellipse, poly, lerp, hx, OUT  # noqa: E402
import make_training_art as tr  # noqa: E402
from make_training_art import guy, standing  # noqa: E402
import make_minigame_art as mg  # noqa: E402  (뛰기 포즈 RUN 과 PAL 의 감독·연습복 색)

ROOT = os.path.join(os.path.dirname(__file__), "..")
META = os.path.join(ROOT, "godot", "scripts", "characters", "cutscene_meta.gd")

VIEW_W, VIEW_H = 211, 98
PANEL_H = 98
CELL_W, CELL_H = 96, 96
FOOT = (48, 90)

PAL.update({k: hx(v) for k, v in {
    # 노을 하늘 (위 → 아래: 보라 → 주황 → 살구)
    "dusk0": "#3b3f7a", "dusk1": "#6a4f8c", "dusk2": "#b8608a", "dusk3": "#ef8a6a", "dusk4": "#ffb37a", "dusk5": "#ffd29a",
    "sun": "#fff1c2", "sun_hi": "#ffffff",
    "stand": "#3a2f55", "stand_s": "#2a2342", "crowd_a": "#57457a", "crowd_b": "#6d5690", "crowd_c": "#8a6aa2",
    "light_pole": "#2a2342", "light_on": "#fff6d8",
    "grass_hi": "#a3c26a", "grass": "#6f9e4e", "grass_s": "#4f7d3c", "grass_dk": "#355a2c",
    "dirt_hi": "#f2bd84", "dirt": "#d9955e", "dirt_s": "#b0703f", "dirt_dk": "#7e4a2a",
    "chalk": "#fff6e8", "chalk_s": "#e8d6bd", "bag_hi": "#ffffff", "bag": "#f1ede6", "bag_s": "#c9c0b2", "bag_dk": "#8f8676",
    "wall": "#2c5a4a", "wall_s": "#1f4436", "pad": "#24493b",
    # 덕아웃 안 (차갑고 어둡게) · 실루엣
    "dug_roof": "#141629", "dug_wall": "#22263f", "dug_wall_s": "#1a1d33", "dug_floor": "#2c2f48", "dug_bench": "#4a3a34",
    "dug_bench_s": "#33282a", "rail": "#1b1d30", "rail_hi": "#4a4f73",
    "sil": "#161a2e", "sil_s": "#0f1222", "sil_rim": "#e6935f",
    "bokeh_a": "#3a3560", "bokeh_b": "#5a4670", "bokeh_c": "#8a5a70",
    # 얼굴 (따뜻한 노을빛이 왼쪽에서)
    "face_hi": "#ffd9b8", "face": "#eeb48e", "face_s": "#c47e62", "face_dk": "#8a4f4a",
    "hair2": "#2b1f1c", "hair2_hi": "#4a3530", "brow": "#2b1f1c", "lip": "#b86a5e", "eye_w": "#fff6ee", "iris": "#2b2140",
    "us_cap_hi": "#5d8cf2", "us_cap": "#2f62e0", "us_cap_s": "#21399c", "us_cap_dk": "#161f66",
    "jersey2_hi": "#ffffff", "jersey2": "#e8ecf5", "jersey2_s": "#a9b0cd", "jersey2_dk": "#5d6590",
    "sweat2": "#d8ecff",
    "glove2_hi": "#d69458", "glove2": "#b0703a", "glove2_s": "#6e3f18", "glove2_dk": "#4a2a14",
    "sock_hi": "#5d8cf2", "sock": "#2f62e0", "sock_s": "#21399c", "sock_dk": "#161f66",
    "shoe2_hi": "#4d5068", "shoe2": "#2a2c3c", "shoe2_s": "#171823", "shoe2_dk": "#0f1018",
    "spike": "#c9ced8",
}.items()})

# 경기복 (흰 유니폼 + 파랑 헬멧): 연습복 색 자리를 잠깐 바꿔 그린다
UNIFORM = {"shirt": "jersey", "shirt_s": "jersey_s", "pants": "jersey", "pants_s": "jersey_s", "cap": "us", "cap_s": "us_s"}
COACH = {"shirt": "coach_suit", "shirt_s": "coach_suit_s", "pants": "coach_suit", "pants_s": "coach_suit_s",
         "cap": "coach_cap", "cap_s": "coach_cap_s"}
UMP = {"shirt": "ump", "shirt_s": "ump_s", "pants": "upants", "pants_s": "upants_s", "cap": "ump", "cap_s": "ump_s"}


def rect(c, x0, y0, x1, y1, color, alpha=255):
    x0, y0, x1, y1 = (int(round(v)) for v in (x0, y0, x1, y1))
    c.rgba[max(0, y0):min(c.h, y1), max(0, x0):min(c.w, x1)] = (*PAL[color], alpha)


def dot(c, x, y, color, alpha=255):
    if 0 <= x < c.w and 0 <= y < c.h:
        c.rgba[int(y), int(x)] = (*PAL[color], alpha)


def flip(c):
    c.rgba = np.ascontiguousarray(c.rgba[:, ::-1])
    return c


def bands(c, y0, y1, names):
    """하늘처럼 위에서 아래로 색 띠 (경계는 1칸 바둑판으로 섞는다 — 도트 디더)"""
    n = len(names)
    step = (y1 - y0) / n
    for i, name in enumerate(names):
        a, b = int(y0 + i * step), int(y0 + (i + 1) * step)
        rect(c, 0, a, c.w, b, name)
        if i + 1 < n:
            for x in range(0, c.w, 2):
                dot(c, x + (b % 2), b, names[i + 1])
                dot(c, x + ((b + 1) % 2), b - 1, names[i + 1])


def crowd(c, y0, y1, seed):
    """관중석 실루엣: 줄마다 머리 점 (번호로 정해지는 가짜 난수 — 원칙 2)"""
    rect(c, 0, y0, c.w, y1, "stand")
    for row, y in enumerate(range(y0 + 2, y1 - 1, 3)):
        for x in range(row % 2, c.w, 3):
            h = (x * 7349 + row * 911 + seed * 131) % 11
            if h < 8:
                dot(c, x, y, ["crowd_a", "crowd_b", "crowd_c"][h % 3])
    rect(c, 0, y1 - 1, c.w, y1, "stand_s")


def light_pole(c, x, top, bottom):
    rect(c, x, top + 6, x + 1, bottom, "light_pole")
    rect(c, x - 4, top, x + 5, top + 5, "light_pole")
    for i in range(3):
        for j in range(2):
            dot(c, x - 3 + i * 3, top + 1 + j * 2, "light_on")


# ---------- 판 (배경) ----------

def panel_dugout():
    """덕아웃 안에서 밖을 본다: 위는 어두운 지붕, 가운데 밝은 노을 운동장, 아래는 난간과 앞줄 의자"""
    c = Cell(320, PANEL_H, 0, 0)
    bands(c, 14, 40, ["dusk2", "dusk3", "dusk4", "dusk5"])
    crowd(c, 30, 42, 1)
    for x in (60, 190, 290):
        light_pole(c, x, 18, 33)
    rect(c, 0, 42, 320, 46, "wall")
    rect(c, 0, 46, 320, 56, "grass")
    for x in range(0, 320, 16):  # 잔디 결
        rect(c, x, 46, x + 8, 56, "grass_hi")
    rect(c, 0, 56, 320, 64, "dirt")
    rect(c, 0, 56, 320, 57, "dirt_hi")
    # 지붕 (위) · 기둥
    rect(c, 0, 0, 320, 14, "dug_roof")
    rect(c, 0, 13, 320, 14, "rail_hi")
    for x in (0, 150, 312):
        rect(c, x, 0, x + 8, 64, "dug_roof")
        rect(c, x + 7, 14, x + 8, 64, "rail_hi")
    # 덕아웃 바닥·의자 (아래 어둡게)
    rect(c, 0, 64, 320, 98, "dug_floor")
    rect(c, 0, 64, 320, 66, "rail")
    rect(c, 0, 66, 320, 67, "rail_hi")
    rect(c, 0, 84, 320, 90, "dug_bench")
    rect(c, 0, 89, 320, 90, "dug_bench_s")
    return c


def panel_dark():
    """얼굴 클로즈업 뒤: 덕아웃 안 어둠 + 흐릿한 빛망울 (먼 조명)"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 98, ["dug_roof", "dug_wall_s", "dug_wall", "dug_wall_s"])
    for i, (x, y, r, col) in enumerate([(30, 20, 6, "bokeh_a"), (70, 34, 4, "bokeh_b"), (160, 18, 7, "bokeh_a"),
                                        (200, 40, 5, "bokeh_c"), (120, 60, 3, "bokeh_b"), (220, 70, 6, "bokeh_a"), (14, 66, 4, "bokeh_c")]):
        c.put(c._mask(circle((x, y), r)), PAL[col])
    return c


def panel_plate():
    """홈플레이트 옆모습: 노을 하늘, 먼 관중석, 흙, 포수(오른쪽, 쪼그려 앉음)"""
    c = Cell(260, PANEL_H, 0, 0)
    bands(c, 0, 46, ["dusk0", "dusk1", "dusk2", "dusk3", "dusk4"])
    c.put(c._mask(circle((40, 34), 9)), PAL["sun"])
    c.put(c._mask(circle((40, 34), 6)), PAL["sun_hi"])
    crowd(c, 40, 58, 2)
    light_pole(c, 120, 22, 40)
    rect(c, 0, 58, 260, 62, "wall")
    rect(c, 0, 62, 260, 98, "dirt")
    rect(c, 0, 62, 260, 63, "dirt_hi")
    for x in range(0, 260, 7):
        dot(c, x, 70 + (x * 3) % 9, "dirt_s")
        dot(c, x + 3, 80 + (x * 5) % 11, "dirt_s")
    # 타석 분필 선 · 홈플레이트 (옆에서 보면 얇다)
    rect(c, 40, 89, 170, 90, "chalk")
    c.part(poly([(92, 88), (106, 88), (104, 90), (94, 90)]), "bag")
    # 포수: 타자 뒤(왼쪽)에 쪼그려 앉아 투수 쪽(오른쪽)으로 미트를 내민다
    px, py = 62, 89
    c.part(ellipse((px - 2, py - 4), 7, 4), "them_s")
    c.part(capsule((px + 2, py - 14), (px - 4, py - 6), 10), "gear")
    c.part(circle((px + 3, py - 19), 5), "gear", "gear_s")
    c.part(ellipse((px + 10, py - 13), 5, 4), "glove", "glove_s")
    return c


def panel_baseline():
    """1루 가는 길 옆모습 (넓다): 뒤는 외야 펜스와 노을, 가운데 잔디, 앞은 흙과 분필 선. 카메라가 흐른다"""
    w = 480
    c = Cell(w, PANEL_H, 0, 0)
    bands(c, 0, 34, ["dusk1", "dusk2", "dusk3", "dusk4", "dusk5"])
    crowd(c, 24, 38, 3)
    for x in range(20, w, 110):
        light_pole(c, x, 8, 24)
    rect(c, 0, 38, w, 50, "wall")
    rect(c, 0, 38, w, 39, "pad")
    for x in range(0, w, 40):  # 펜스 이음새 (흐름이 보이게)
        rect(c, x, 39, x + 1, 50, "wall_s")
    rect(c, 0, 50, w, 70, "grass")
    for x in range(0, w, 24):
        rect(c, x, 50, x + 12, 70, "grass_hi")
    rect(c, 0, 70, w, 98, "dirt")
    rect(c, 0, 70, w, 71, "dirt_hi")
    for x in range(0, w, 9):
        dot(c, x, 76 + (x * 7) % 13, "dirt_s")
        dot(c, x + 4, 88 + (x * 3) % 7, "dirt_dk")
    rect(c, 0, 92, w, 94, "chalk")  # 1루선
    return c


def panel_bag():
    """1루 베이스 바로 옆, 땅 높이: 크게 보이는 베이스, 흙 알갱이, 위로 노을 하늘"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 52, ["dusk2", "dusk3", "dusk4", "dusk5"])
    crowd(c, 40, 52, 4)
    rect(c, 0, 52, 240, 98, "dirt")
    rect(c, 0, 52, 240, 53, "dirt_hi")
    for x in range(0, 240, 5):
        dot(c, x, 58 + (x * 7) % 30, "dirt_s")
        dot(c, x + 2, 62 + (x * 11) % 31, "dirt_dk")
        dot(c, x + 1, 56 + (x * 13) % 37, "dirt_hi")
    # 베이스 (가운데, 크게, 옆에서 본 사다리꼴 + 두께)
    c.ramp(poly([(84, 74), (156, 74), (172, 86), (68, 86)]), "bag", 1)
    rect(c, 68, 86, 173, 90, "bag_s")
    rect(c, 68, 89, 173, 90, "bag_dk")
    rect(c, 0, 90, 68, 92, "chalk")
    return c


def panel_sky():
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 98, ["dusk0", "dusk1", "dusk2", "dusk3", "dusk4", "dusk5"])
    crowd(c, 70, 90, 5)
    rect(c, 0, 90, 240, 98, "dirt")
    return c


def panel_wide():
    """운동장 전체, 노을: 위 하늘, 관중석, 넓은 외야 잔디, 내야 흙 다이아몬드(납작), 왼쪽 아래 덕아웃"""
    c = Cell(260, PANEL_H, 0, 0)
    bands(c, 0, 36, ["dusk0", "dusk1", "dusk2", "dusk3", "dusk4", "dusk5"])
    c.put(c._mask(ellipse((190, 34), 14, 6)), PAL["sun"])
    crowd(c, 30, 44, 6)
    for x in (30, 130, 230):
        light_pole(c, x, 14, 30)
    rect(c, 0, 44, 260, 48, "wall")
    rect(c, 0, 48, 260, 98, "grass")
    for y in range(50, 98, 8):  # 잔디 줄무늬 (멀수록 촘촘하게 보이도록 가로 띠)
        rect(c, 0, y, 260, y + 3, "grass_hi")
    c.part(poly([(130, 60), (230, 78), (130, 98), (30, 78)]), "dirt", "dirt_s", outline=False)
    c.part(poly([(130, 66), (200, 78), (130, 92), (60, 78)]), "grass", outline=False)
    for (x, y) in [(130, 61), (226, 78), (34, 78)]:
        c.part(poly([(x - 3, y), (x, y - 1), (x + 3, y), (x, y + 1)]), "bag", outline=False)
    # 덕아웃 (왼쪽 아래 어두운 띠)
    rect(c, 0, 84, 46, 98, "dug_roof")
    rect(c, 0, 84, 46, 85, "rail_hi")
    return c


# ---------- 인물 칸 ----------

def cell():
    return Cell(CELL_W, CELL_H, 0, 0)


def dress(colors, fn):
    mg.swap(colors, fn)


def draw_coach(i):
    """감독: 0 앞을 본다 · 1 고개를 벤치 쪽(왼쪽)으로 — 칸을 뒤집어 왼쪽을 보게 한다"""
    c = cell()
    x, base = 48, FOOT[1] - tr.FLOOR

    def body():
        k = standing(x, fe=(x + 2, 41), fh=(x + 1, 48), be=(x - 1, 41), bh=(x - 2, 48))
        if i == 1:
            k.update(head=(x + 3, 25), fe=(x + 6, 36), fh=(x + 13, 33))  # 고개를 돌리고 손짓
        for kk, v in list(k.items()):
            k[kk] = (v[0], v[1] + base)
        guy(c, k)
    dress(COACH, body)
    return flip(c)


def draw_bench(i):
    """덕아웃 앞줄 선수 등 실루엣 (노을빛 테두리가 오른쪽 위에): 0 앉음 · 1 일어나 난간에 붙음"""
    c = cell()
    x, y = 48, FOOT[1]
    if i == 0:
        head, sh, hip = (x, y - 34), (x, y - 26), (x, y - 8)
    else:
        head, sh, hip = (x + 3, y - 52), (x + 2, y - 44), (x, y - 22)
    c.part(capsule(sh, hip, 18), "sil", "sil_s")
    c.part(capsule((sh[0] - 9, sh[1] + 2), (sh[0] + 9, sh[1] + 2), 8), "sil", "sil_s")
    c.part(circle(head, 7), "sil", "sil_s")
    c.part(ellipse((head[0], head[1] - 3), 7.5, 4.5), "sil")  # 모자
    if i == 1:  # 난간을 잡은 팔
        c.part(capsule((sh[0] - 9, sh[1] + 2), (sh[0] - 12, y - 30), 5), "sil")
        c.part(capsule((sh[0] + 9, sh[1] + 2), (sh[0] + 12, y - 30), 5), "sil")
    # 오른쪽 위 가장자리에 노을빛 (실루엣이 판에 묻히지 않게)
    solid = c.rgba[..., 3] == 255
    rim = solid & ~np.roll(solid, -1, axis=1) | solid & ~np.roll(solid, 1, axis=0)
    rim &= c.rgba[..., 0] < 60
    c.put(rim & (np.arange(CELL_W)[None, :] > x - 2), PAL["sil_rim"])
    return c


def draw_face(i):
    """김성호 얼굴 클로즈업 (왼쪽 3/4, 노을빛이 왼쪽에서): 0 고개 숙임 · 1 눈 듦 · 2 이를 문다(땀)"""
    c = cell()
    cx, cy = 50, 46 + (6 if i == 0 else 0)
    # 어깨·목 (유니폼)
    c.ramp(poly([(8, 96), (16, 78), (34, 70), (66, 70), (86, 80), (92, 96)]), "jersey2", 2)
    c.ramp(poly([(40, 60), (60, 60), (62, 74), (38, 74)]), "face", 1)
    rect(c, 30, 72, 70, 74, "us_cap_s")  # 깃
    # 얼굴 (턱이 조금 뾰족한 둥근 판)
    c.ramp([ellipse((cx, cy), 21, 23), poly([(cx - 15, cy + 10), (cx + 15, cy + 10), (cx + 3, cy + 24), (cx - 5, cy + 24)])], "face", 2)
    # 귀 (오른쪽)
    c.ramp(ellipse((cx + 21, cy + 2), 4, 6), "face", 1)
    # 머리카락 (모자 아래 옆머리)
    c.part(poly([(cx + 12, cy - 14), (cx + 22, cy - 10), (cx + 22, cy - 2), (cx + 16, cy - 6)]), "hair2", "hair2_hi")
    # 모자 (파랑) + 챙
    c.ramp(ellipse((cx + 1, cy - 14), 23, 13), "us_cap", 2)
    brim_y = cy - 6
    c.ramp(poly([(cx - 26, brim_y - 1), (cx + 14, brim_y - 2), (cx + 16, brim_y + 2), (cx - 22, brim_y + 4)]), "us_cap", 1)
    # 챙 그늘이 이마·눈썹을 덮는다
    shade = c._mask(poly([(cx - 20, brim_y + 4), (cx + 15, brim_y + 2), (cx + 15, brim_y + 7), (cx - 18, brim_y + 9)]))
    face_px = (c.rgba[..., :3] == PAL["face"]).all(-1) | (c.rgba[..., :3] == PAL["face_hi"]).all(-1)
    c.put(shade & face_px, PAL["face_s"])
    ey = cy + 4
    if i == 0:
        # 고개 숙임: 눈은 감은 듯 가는 선, 챙 그늘이 깊다
        for ex in (cx - 11, cx + 6):
            rect(c, ex, ey + 1, ex + 7, ey + 2, "face_dk")
        rect(c, cx - 4, cy + 15, cx + 4, cy + 16, "face_dk")
    else:
        # 눈 듦: 흰자 · 검은자(위를 본다) · 반짝임 한 칸 (애니메이션풍 눈)
        for ex, w in ((cx - 12, 8), (cx + 5, 7)):
            rect(c, ex, ey - 1, ex + w, ey + 4, "eye_w")
            rect(c, ex + 2, ey - 1, ex + w - 1, ey + 4, "iris")
            dot(c, ex + 3, ey, "eye_w")
            rect(c, ex - 1, ey - 2, ex + w + 1, ey - 1, "brow")  # 위 눈꺼풀
        # 눈썹 (안쪽이 올라간다 — 놀람 / 2 는 내려간다 — 결심)
        if i == 1:
            rect(c, cx - 13, ey - 6, cx - 5, ey - 5, "brow")
            rect(c, cx + 5, ey - 6, cx + 12, ey - 5, "brow")
            dot(c, cx - 6, ey - 7, "brow")
            dot(c, cx + 5, ey - 7, "brow")
            rect(c, cx - 3, cy + 15, cx + 3, cy + 17, "face_dk")  # 살짝 벌어진 입
        else:
            rect(c, cx - 13, ey - 5, cx - 5, ey - 4, "brow")
            rect(c, cx + 5, ey - 5, cx + 12, ey - 4, "brow")
            dot(c, cx - 6, ey - 4, "brow")
            dot(c, cx + 5, ey - 4, "brow")
            rect(c, cx - 5, cy + 15, cx + 5, cy + 16, "lip")  # 꽉 다문 입
            c.part(ellipse((cx - 18, cy + 2), 1.2, 2.2), "sweat2", outline=False)
    # 코 그늘
    rect(c, cx - 2, cy + 9, cx, cy + 11, "face_s")
    return c


def run_pose(i, x=48):
    bob = [0, 1, 0, 1][i]
    k = standing(x, head=(x + 4, 26 + bob), sh=(x + 2, 34 + bob), hip=(x, 48 + bob), **mg.RUN[i](x))
    base = FOOT[1] - tr.FLOOR
    return {kk: (v[0], v[1] + base) for kk, v in k.items()}


def draw_batter(i):
    """김성호 타석 (오른쪽을 본다): 0 준비 · 1 내딛기 · 2 맞는 순간 · 3 팔로스루 — make_minigame_art 의 타격 포즈를 경기복으로"""
    c = cell()
    x = 46
    poses = [
        standing(x, be=(x - 6, 36), bh=(x - 4, 30), fe=(x - 2, 38), fh=(x - 4, 30), bat=((x - 4, 30), -115)),
        standing(x, fk=(x + 6, 57), fft=(x + 10, tr.FLOOR - 1), be=(x - 7, 37), bh=(x - 5, 31), fe=(x - 3, 39), fh=(x - 5, 31), bat=((x - 5, 31), -140)),
        standing(x + 1, head=(x + 3, 26), sh=(x + 1, 35), fk=(x + 6, 57), fft=(x + 9, tr.FLOOR - 1), be=(x + 1, 42), bh=(x + 7, 45), fe=(x + 5, 40), fh=(x + 7, 45), bat=((x + 7, 45), 2)),
        standing(x + 1, head=(x + 3, 26), sh=(x + 1, 35), fk=(x + 6, 57), fft=(x + 9, tr.FLOOR - 1), bk=(x - 1, 58), bft=(x - 3, tr.FLOOR - 2),
                 be=(x + 5, 37), bh=(x + 3, 30), fe=(x + 6, 35), fh=(x + 3, 30), bat=((x + 3, 30), -165)),
    ]
    base = FOOT[1] - tr.FLOOR
    k = {kk: ((v[0][0], v[0][1] + base), v[1]) if kk == "bat" else (v[0], v[1] + base) for kk, v in poses[i].items()}
    dress(UNIFORM, lambda: guy(c, k))
    if i == 2:
        for d in [(-3, -4), (4, -5), (5, 2), (-2, 4), (6, -1)]:
            c.put(c._mask(circle((x + 30 + d[0] * 1.4, 45 + base + d[1] * 1.4), 0.7)), PAL["line"])
    return c


def draw_run(i):
    """0~3 달리기 (헬멧) · 4~7 달리기 (헬멧이 벗겨져 머리카락이 날린다)"""
    c = cell()
    k = run_pose(i % 4)
    if i < 4:
        dress(UNIFORM, lambda: guy(c, k))
    else:
        dress(UNIFORM, lambda: guy(c, k, cap=False))
        hx_, hy = k["head"]
        c.part(ellipse((hx_ - 1, hy - 3), 6.5, 4), "hair2", "hair2_hi")
        c.part(poly([(hx_ - 6, hy - 4), (hx_ - 11, hy - 6 + (i % 2)), (hx_ - 6, hy - 1)]), "hair2")  # 뒤로 날리는 머리
        c.put(c._mask(poly([(hx_ + 2, hy), (hx_ + 6, hy), (hx_ + 6, hy + 1), (hx_ + 2, hy + 1)])), PAL["ink"])  # 눈 (옆)
    # 바람 줄 (뒤쪽)
    for j, y in enumerate((30, 42, 54)):
        rect(c, 10 + (i * 3 + j * 5) % 8, y + FOOT[1] - tr.FLOOR, 22 + (i * 3 + j * 5) % 8, y + 1 + FOOT[1] - tr.FLOOR, "line")
    return c


def draw_helmet(i):
    """날아가는 헬멧: 장마다 기울기를 따로 찍는다 (회전 없이)"""
    c = cell()
    cx, cy = 48, 70
    if i == 0:
        c.ramp(ellipse((cx, cy), 8, 6.5), "us_cap", 1)
        c.ramp(poly([(cx + 4, cy + 2), (cx + 12, cy + 3), (cx + 11, cy + 5), (cx + 3, cy + 5)]), "us_cap", 1)
        c.ramp(ellipse((cx - 3, cy + 4), 3, 3), "us_cap", 1)  # 귀 덮개
    elif i == 1:
        c.ramp(ellipse((cx, cy), 6.5, 8), "us_cap", 1)
        c.ramp(poly([(cx + 2, cy - 6), (cx + 4, cy - 13), (cx + 6, cy - 12), (cx + 5, cy - 5)]), "us_cap", 1)
        c.ramp(ellipse((cx + 3, cy + 4), 3, 3), "us_cap", 1)
    else:
        c.ramp(ellipse((cx, cy), 8, 6.5), "us_cap", 1)
        c.ramp(poly([(cx - 4, cy - 2), (cx - 12, cy - 3), (cx - 11, cy - 5), (cx - 3, cy - 5)]), "us_cap", 1)
        c.ramp(ellipse((cx + 3, cy - 4), 3, 3), "us_cap", 1)
    dot(c, cx - 3, cy - 3, "us_gl")
    dot(c, cx - 2, cy - 3, "us_gl")
    return c


def draw_foot(i):
    """베이스를 밟는 발 (크게, 왼쪽에서 내려온다): 정강이(흰 바지+파랑 양말) + 스파이크"""
    c = cell()
    c.ramp(poly([(30, 0), (52, 0), (56, 60), (36, 62)]), "sock", 2)
    c.ramp(poly([(28, 0), (54, 0), (54, 16), (30, 18)]), "jersey2", 2)
    c.ramp([ellipse((54, 72), 22, 9), poly([(34, 58), (58, 58), (60, 70), (34, 72)])], "shoe2", 2)
    rect(c, 32, 80, 78, 82, "spike")
    for x in range(36, 76, 8):
        rect(c, x, 82, x + 2, 84, "spike")
    rect(c, 40, 66, 70, 67, "shoe2_hi")  # 끈
    return c


def draw_mitt(i):
    """1루수 미트 (크게, 오른쪽에서 내민다): 0 빈 미트 · 1 공이 꽂힘"""
    c = cell()
    c.ramp(poly([(96, 50), (70, 44), (66, 58), (96, 66)]), "jersey2", 2)  # 팔
    c.ramp([ellipse((46, 56), 24, 22)], "glove2", 2)
    c.ramp(ellipse((40, 58), 13, 12), "glove2", 1)
    c.put(c._mask(ellipse((40, 58), 10, 9)), PAL["glove2_dk"])
    for y in range(40, 76, 6):  # 손가락 이음새
        rect(c, 58, y, 66, y + 1, "glove2_dk")
    if i == 1:
        c.ramp(circle((40, 57), 7), "jersey2", 1)
        rect(c, 36, 54, 44, 55, "them")  # 실밥
        rect(c, 36, 60, 44, 61, "them")
    return c


def draw_ump(i):
    """루심 (오른쪽을 본다, 왼쪽 판 하늘 앞): 0 몸을 숙여 지켜봄 · 1 아웃 — 주먹을 들어 올린다"""
    c = cell()
    x = 48
    base = FOOT[1] - tr.FLOOR
    if i == 0:
        k = standing(x, head=(x + 5, 30), sh=(x + 3, 38), hip=(x - 2, 51), fk=(x + 5, 58), bk=(x - 6, 58),
                     fe=(x + 6, 46), fh=(x + 7, 52), be=(x - 1, 46), bh=(x + 1, 52))
    else:
        k = standing(x, fe=(x + 4, 24), fh=(x + 2, 15), be=(x - 2, 41), bh=(x - 1, 48))
    k = {kk: (v[0], v[1] + base) for kk, v in k.items()}
    dress(UMP, lambda: guy(c, k))
    if i == 1:
        c.part(circle(k["fh"], 2.5), "skin", "skin_s")  # 주먹
    return c


def draw_tiny(i):
    """먼 김성호 (작게, 16칸 키): 0 서기 (오른쪽을 본다) · 1 덕아웃 쪽(왼쪽)으로 돌아섬 · 2 허리 숙여 인사"""
    c = cell()
    x, y = 48, FOOT[1]
    face_left = i >= 1
    s = -1 if face_left else 1
    if i < 2:
        c.part(capsule((x, y - 12), (x, y - 6), 5), "jersey2", "jersey2_s")
        c.part(capsule((x - 1, y - 6), (x - 2, y), 2), "jersey2_s")
        c.part(capsule((x + 1, y - 6), (x + 2, y), 2), "jersey2_s")
        c.part(circle((x + s, y - 15), 2.5), "hair2")
        dot(c, x + s * 2, y - 15, "face")
    else:
        c.part(capsule((x - 1, y - 6), (x - 2, y), 2), "jersey2_s")
        c.part(capsule((x + 1, y - 6), (x + 2, y), 2), "jersey2_s")
        c.part(capsule((x, y - 7), (x - 6, y - 10), 5), "jersey2", "jersey2_s")
        c.part(circle((x - 9, y - 9), 2.5), "hair2")
    c.put(c._mask(ellipse((x, y + 1), 4, 1)), PAL["ink"], 70)  # 발밑 그림자 (Cell.shadow 는 칸 원점에 그린다)
    return c


def draw_tiny_helmet(i):
    c = cell()
    c.part(ellipse((48, FOOT[1] - 1), 2.5, 1.5), "us_cap", "us_cap_s")
    return c


PANELS = [("dugout", panel_dugout), ("dark", panel_dark), ("plate", panel_plate), ("baseline", panel_baseline),
          ("bag", panel_bag), ("sky", panel_sky), ("wide", panel_wide)]
SPRITES = [("coach", draw_coach, 2), ("bench", draw_bench, 2), ("face", draw_face, 3), ("batter", draw_batter, 4),
           ("run", draw_run, 8), ("helmet", draw_helmet, 3), ("foot", draw_foot, 1), ("mitt", draw_mitt, 2),
           ("ump", draw_ump, 2), ("tiny", draw_tiny, 3), ("tiny_helmet", draw_tiny_helmet, 1)]


def main():
    os.makedirs(OUT, exist_ok=True)
    panels = [(name, fn()) for name, fn in PANELS]
    pw = max(p.w for _, p in panels)
    bg = Image.new("RGBA", (pw, PANEL_H * len(panels)), (0, 0, 0, 0))
    for i, (_, p) in enumerate(panels):
        bg.paste(p.image(), (0, i * PANEL_H))
    bg.save(os.path.join(OUT, "cutscene_bg.png"))
    cols = max(n for _, _, n in SPRITES)
    sheet = Image.new("RGBA", (CELL_W * cols, CELL_H * len(SPRITES)), (0, 0, 0, 0))
    for r, (_, fn, n) in enumerate(SPRITES):
        for i in range(n):
            sheet.paste(fn(i).image(), (i * CELL_W, r * CELL_H))
    sheet.save(os.path.join(OUT, "cutscene.png"))
    with open(META, "w", encoding="utf-8") as f:
        f.write("class_name CutsceneMeta\nextends RefCounted\n")
        f.write("## scripts/make_cutscene_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.\n")
        f.write("## art/pixel/cutscene_bg.png: 줄 = 판 (높이 PANEL_H, 넓이는 판마다) · art/pixel/cutscene.png: 줄 = 인물 동작, 칸 = 장\n\n")
        f.write("## 카메라가 보는 넓이·높이 (그림 픽셀, 화면에서 SCALE 배)\n")
        f.write("const VIEW := Vector2(%d, %d)\nconst SCALE := 4\nconst PANEL_H := %d\n" % (VIEW_W, VIEW_H, PANEL_H))
        f.write("## 판 이름 -> [줄, 넓이]\nconst PANELS := {%s}\n" % ", ".join('"%s": [%d, %d]' % (n, i, p.w) for i, (n, p) in enumerate(panels)))
        f.write("const CELL := Vector2(%d, %d)\n## 칸 안 발 자리 (인물을 판에 놓을 때 이 점을 맞춘다)\nconst FOOT := Vector2(%d, %d)\n" % (CELL_W, CELL_H, *FOOT))
        f.write("## 인물 이름 -> [줄, 장 수]\nconst ROWS := {%s}\n" % ", ".join('"%s": [%d, %d]' % (n, r, k) for r, (n, _, k) in enumerate(SPRITES)))
    print("판 %d장 · 인물 %d줄" % (len(panels), len(SPRITES)))


if __name__ == "__main__":
    main()
