#!/usr/bin/env python3
"""컷신 도트 (2026-11-05): 이야기 카드 사이에 끼는 짧은 "애니메이션 영화" 장면의 그림을 코드로 찍어 만든다.
AI 생성 그림을 쓰지 않는다. 기본 도구(Cell·도형)는 make_pixel_art.py, 뼈대 포즈는 make_training_art.py · make_minigame_art.py 의 것.

컷신 그림 규칙 (CLAUDE.md 원칙 6, 2026-11-05 유저 결정):
    - 인물은 실루엣으로만 그린다. 누구의 얼굴도 그리지 않는다 (눈·코·입·머리카락 결 없음).
    - 얼굴 밖의 것도 자세히 그리지 않는다 — 큰 덩어리와 윤곽, 노을 테두리빛(rim)으로 보인다.
      실루엣 = 한 색 덩어리(sil) + 빛을 받는 가장자리 1칸(sil_rim, 위쪽은 더 밝은 sil_rim_hi).
    - 인물은 도형 조립이 아니라 **손으로 찍은 글자 격자**(scripts/cutscene_grids.py)로 그린다 (2026-11-05 유저 지시).
    - 장 수는 적게 (컷마다 인물 1~2장). 움직임은 카메라 이동·자리 이동(path)·히트스톱(hold)·번쩍임(flash)·흔들림으로 낸다.
    - 판(배경)은 역광 구도의 큰 덩어리: 큰 해·노을 띠 3~4개·어두운 땅과 관중석.

게임 화면(중계 카메라)을 그대로 쓰지 않는다 — 컷마다 따로 그린 판(배경)에 카메라를 대고, 인물 칸을 놓는다.
화면 4배 (그림 211×98 = 화면 844×392). 판은 카메라가 좌우로 움직일 만큼 넓게 그린다.

컷신 (content.json cutscenes — 1학년 에피소드마다 하나 + 사이드 스토리 둘):
    ep01_join 입부 첫날 · ep02_bench 차례는 오지 않았다 · ep03_sink 청운고 수돗가 · ep04_first 고교 첫 타석 ·
    ep05_seongho 김성호의 마지막 타석 · ep06_dp 6-4-3 · ep07_sound 소리가 다르다 · ep08_name 이름 ·
    side_harin_museum 하린 미술관 · side_soyul_folder 소율 1학년 폴더

    판 (cutscene_bg.png, 줄 = 판, 높이 98):
        dugout    덕아웃 안에서 운동장을 본다 (어두운 지붕·기둥 + 노을 운동장)
        rail      덕아웃 안 낮은 자리에서 본 노을 하늘과 큰 해 (난간은 인물 앞에 놓는 칸 rail)
        plate     홈플레이트 옆모습: 타자 바로 뒤에 지는 큰 해, 관중석 덩어리, 포수 실루엣
        baseline  1루 가는 길 옆모습 (480 넓이 — 카메라가 달리는 사람을 따라 흐른다)
        bag       1루 베이스 바로 옆, 땅 높이 (지평선에 해)
        sky       낮은 자리에서 올려다본 노을 하늘 (루심·투수)
        wide      운동장 전체 (노을)
        school    새벽의 서림고: 먼 아파트, 컨테이너 부실, 흙 운동장 (EP01)
        cheongun  청운고: 숙소·전광판·인조잔디 (EP03)
        sink      수돗가 (EP03)
        cage_night     겨울밤 배팅 케이지, 전구 하나 (EP07)
        clubroom_night 밤의 컨테이너 부실, 환한 창 하나 (EP07)
        stands    1루 관중석 계단 (EP08)
        museum    미술관: 큰 창빛, 색 덩어리 그림 두 점, 긴 의자 (하린)
        clubroom_in    부실 안, 노트북 화면 하나 (소율)
    인물 칸 (cutscene.png, 칸 96×96, 발 자리 FOOT, 줄 = 인물, 왼쪽부터 장) — 모두 손으로 찍은 실루엣 격자(cutscene_grids.py):
        coach 감독 가리킴 · bench 앉음/일어섬 · rise 뒷모습 어깨 위 · rail 난간(소품) · batter 준비/맞는 순간 ·
        run 달리기 2장 · helmet 헬멧(소품) · foot 발 · mitt 미트 · ump 지켜봄/아웃 · tiny 먼 사람 서기/인사 ·
        tiny_helmet 헬멧(소품) · tiny_walk 먼 사람 서기/걷기 · coach_front 감독 팔짱 · sink 숙임/허리 폄 ·
        pitcher 세트/다리 들기 · toss 유격수 토스 · pivot 2루수 송구 · ball 공 · soyul_window 창가에서 손 듦 ·
        scouts 관중석 두 사람(스피드건 듦/내림) · pair_stand 나란히 선 뒷모습 · pair_sit 나란히 앉은 뒷모습

    python3 scripts/make_cutscene_art.py   (pillow·numpy 필요)

나오는 것: godot/art/pixel/cutscene_bg.png, godot/art/pixel/cutscene.png, godot/scripts/characters/cutscene_meta.gd
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from make_pixel_art import PAL, Cell, capsule, circle, ellipse, poly, hx, shifted, OUT  # noqa: E402
from cutscene_grids import GRIDS  # noqa: E402  (손으로 찍은 인물 격자)

ROOT = os.path.join(os.path.dirname(__file__), "..")
META = os.path.join(ROOT, "godot", "scripts", "characters", "cutscene_meta.gd")

VIEW_W, VIEW_H = 211, 98
PANEL_H = 98
CELL_W, CELL_H = 96, 96
FOOT = (48, 90)

PAL.update({k: hx(v) for k, v in {
    # 노을 하늘 (위 → 아래: 보라 → 주황 → 살구)
    "dusk0": "#3b3f7a", "dusk1": "#6a4f8c", "dusk2": "#b8608a", "dusk3": "#ef8a6a", "dusk4": "#ffb37a", "dusk5": "#ffd29a",
    "sun": "#fff1c2", "sun_hi": "#ffffff", "glow": "#ffe2a8",
    "stand": "#3a2f55", "stand_s": "#2a2342", "light_pole": "#2a2342",
    "grass_hi": "#a3c26a", "grass": "#6f9e4e", "grass_s": "#4f7d3c", "grass_dk": "#355a2c",
    "dirt_hi": "#f2bd84", "dirt": "#d9955e", "dirt_s": "#b0703f", "dirt_dk": "#7e4a2a",
    "chalk": "#fff6e8", "bag_hi": "#ffffff", "bag": "#f1ede6", "bag_s": "#c9c0b2", "bag_dk": "#8f8676",
    "wall": "#2c5a4a", "wall_s": "#1f4436", "pad": "#24493b",
    # 덕아웃 안 (차갑고 어둡게)
    "dug_roof": "#141629", "dug_floor": "#2c2f48", "dug_bench": "#4a3a34", "dug_bench_s": "#33282a",
    "rail": "#1b1d30", "rail_hi": "#4a4f73",
    # 새벽 (EP01) · 밤 (EP07 겨울 케이지·부실 창) · 실내 (미술관·부실 안)
    "dawn0": "#4a5a8c", "dawn1": "#8a7aa8", "dawn2": "#e8a0a0", "dawn3": "#ffd0a8",
    "night0": "#0f1430", "night1": "#1a2248", "night2": "#26305e",
    "concrete": "#5a5470", "concrete_s": "#433e58", "window": "#ffe9b8", "screen": "#cfe8ff", "screen_s": "#8fb8e8",
    "museum_wall": "#2a2440", "frame_a": "#b8608a", "frame_b": "#ef8a6a", "floor": "#3a3050",
    # 실루엣 · 노을 테두리빛
    "sil": "#161a2e", "sil_rim": "#e6935f", "sil_rim_hi": "#ffc58a",
}.items()})


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


def sun(c, x, y, r):
    """지는 해: 빛무리 → 해 → 가운데 흰 빛 (큰 동그라미 셋)"""
    c.put(c._mask(circle((x, y), r + 5)), PAL["glow"])
    c.put(c._mask(circle((x, y), r)), PAL["sun"])
    c.put(c._mask(circle((x, y), r - 4)), PAL["sun_hi"])


def crowd(c, y0, y1, seed):
    """관중석: 한 색 덩어리. 위 가장자리만 들쭉날쭉 (머리 줄 윤곽 — 번호로 정해지는 가짜 난수, 원칙 2)"""
    rect(c, 0, y0, c.w, y1, "stand")
    for x in range(0, c.w, 3):
        h = (x * 7349 + seed * 131) % 7 % 3
        rect(c, x, y0 - h, x + 2, y0, "stand")
    rect(c, 0, y1 - 1, c.w, y1, "stand_s")


def light_pole(c, x, top, bottom):
    rect(c, x, top + 6, x + 1, bottom, "light_pole")
    rect(c, x - 4, top, x + 5, top + 5, "light_pole")


# ---------- 실루엣 ----------

def rim(c, mask=None, keep=None):
    """빛을 받는 가장자리 1칸: 옆(왼·오른) = sil_rim, 위 = sil_rim_hi. 칸 가장자리에서 잘린 곳은 빼고,
    keep(격자의 '=' 칸 — 일부러 빛을 끊은 곳)도 뺀다"""
    solid = (c.rgba[..., 3] == 255) if mask is None else mask
    side = solid & (~shifted(solid, -1, 0) | ~shifted(solid, 1, 0))
    top = solid & ~shifted(solid, 0, -1)
    for m in (side, top):
        m[0, :] = m[-1, :] = m[:, 0] = m[:, -1] = False
        if keep is not None:
            m &= ~keep
    c.put(side, PAL["sil_rim"])
    c.put(top, PAL["sil_rim_hi"])


def silhouette(c, fns):
    """(판 소품용) 도형 여러 개를 한 덩어리 실루엣으로"""
    m = c._mask(fns)
    c.put(m, PAL["sil"])
    return m


def stamp_grid(name, flip_x=False):
    """손으로 찍은 격자 (cutscene_grids.GRIDS) 하나를 칸에 찍는다: 격자의 기준점이 칸의 FOOT 에 온다. '#' 은 테두리빛을 받고 '=' 는 받지 않는다"""
    rows, anchor = GRIDS[name]
    c = cell()
    keep = np.zeros((CELL_H, CELL_W), bool)
    w = max(len(r) for r in rows)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            gx = (w - 1 - x) if flip_x else x
            ax = (w - 1 - anchor[0]) if flip_x else anchor[0]
            cx, cy = FOOT[0] - ax + gx, FOOT[1] - anchor[1] + y
            if 0 <= cx < CELL_W and 0 <= cy < CELL_H:
                c.rgba[cy, cx] = (*PAL["sil"], 255)
                keep[cy, cx] = ch == "="
    rim(c, keep=keep)
    return c


def cell():
    return Cell(CELL_W, CELL_H, 0, 0)


# ---------- 판 (배경) ----------

def panel_dugout():
    """덕아웃 안에서 밖을 본다: 위는 어두운 지붕, 가운데 밝은 노을 운동장(실루엣이 이 밝은 띠 앞에서 읽힌다), 아래는 난간과 앞줄 의자"""
    c = Cell(320, PANEL_H, 0, 0)
    bands(c, 14, 40, ["dusk2", "dusk3", "dusk4", "dusk5"])
    sun(c, 230, 34, 9)
    crowd(c, 32, 42, 1)
    for x in (60, 200):
        light_pole(c, x, 18, 33)
    rect(c, 0, 42, 320, 46, "wall")
    rect(c, 0, 46, 320, 64, "grass_s")
    rect(c, 0, 56, 320, 57, "dirt")
    # 지붕 · 기둥
    rect(c, 0, 0, 320, 14, "dug_roof")
    rect(c, 0, 13, 320, 14, "rail_hi")
    for x in (0, 150, 312):
        rect(c, x, 0, x + 8, 64, "dug_roof")
    # 덕아웃 바닥·의자 (아래 어둡게)
    rect(c, 0, 64, 320, 98, "dug_floor")
    rect(c, 0, 64, 320, 66, "rail")
    rect(c, 0, 66, 320, 67, "rail_hi")
    rect(c, 0, 84, 320, 90, "dug_bench")
    rect(c, 0, 89, 320, 90, "dug_bench_s")
    return c


def panel_rail():
    """덕아웃 안 낮은 자리에서 본 밖: 위 지붕 끝, 노을 하늘에 큰 해, 먼 관중석 덩어리. 난간·앞벽은 칸 rail 로 인물 앞에 놓는다"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 10, 62, ["dusk2", "dusk3", "dusk4"])
    sun(c, 118, 44, 17)
    crowd(c, 52, 62, 7)
    light_pole(c, 30, 30, 52)
    light_pole(c, 206, 28, 52)
    rect(c, 0, 62, 240, 98, "grass_dk")
    rect(c, 0, 0, 240, 10, "dug_roof")
    rect(c, 0, 9, 240, 10, "rail_hi")
    return c


def panel_plate():
    """홈플레이트 옆모습: 노을 하늘, 타자 바로 뒤에 지는 큰 해(타자가 역광 실루엣이 된다), 관중석 덩어리, 흙, 포수 실루엣"""
    c = Cell(260, PANEL_H, 0, 0)
    bands(c, 0, 50, ["dusk1", "dusk2", "dusk3", "dusk4"])
    sun(c, 128, 48, 21)
    crowd(c, 46, 60, 2)
    light_pole(c, 40, 22, 46)
    light_pole(c, 222, 24, 46)
    rect(c, 0, 60, 260, 63, "wall_s")
    rect(c, 0, 63, 260, 98, "dirt_dk")
    rect(c, 0, 63, 260, 64, "dirt_s")
    rect(c, 40, 86, 200, 87, "dirt")  # 타석 선 (빛을 받은 흙 한 줄)
    # 포수: 타자 뒤(왼쪽)에 쪼그려 앉아 미트를 내민다 — 한 덩어리 (발은 아래 검은 띠 위에)
    px, py = 62, 86
    m = silhouette(c, [ellipse((px - 2, py - 4), 8, 4), capsule((px + 2, py - 16), (px - 4, py - 6), 13),
                       circle((px + 3, py - 22), 6.5), ellipse((px + 12, py - 15), 6, 5)])
    rim(c, m)
    return c


def panel_baseline():
    """1루 가는 길 옆모습 (넓다): 노을과 해, 관중석 덩어리, 펜스(이음새가 흐름을 보인다), 잔디 줄, 흙, 1루선"""
    w = 480
    c = Cell(w, PANEL_H, 0, 0)
    bands(c, 0, 36, ["dusk2", "dusk3", "dusk4"])
    sun(c, 70, 30, 13)
    crowd(c, 26, 40, 3)
    for x in range(20, w, 110):
        light_pole(c, x, 8, 26)
    rect(c, 0, 40, w, 50, "wall")
    rect(c, 0, 40, w, 41, "pad")
    for x in range(0, w, 40):
        rect(c, x, 41, x + 2, 50, "wall_s")
    rect(c, 0, 50, w, 70, "grass_dk")
    rect(c, 0, 70, w, 98, "dirt_dk")
    rect(c, 0, 70, w, 71, "dirt_s")
    rect(c, 0, 86, w, 88, "chalk")  # 1루선 (달리는 발 바로 밑 — 아래 검은 띠에 가리지 않게)
    return c


def panel_bag():
    """1루 베이스 바로 옆, 땅 높이: 지평선에 해, 크게 보이는 베이스(빛을 받아 밝다), 그늘진 흙"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 52, ["dusk2", "dusk3", "dusk4", "dusk5"])
    sun(c, 176, 48, 15)
    crowd(c, 44, 52, 4)
    rect(c, 0, 52, 240, 98, "dirt_dk")
    rect(c, 0, 52, 240, 53, "dirt_s")
    c.ramp(poly([(84, 74), (156, 74), (172, 86), (68, 86)]), "bag", 1)
    rect(c, 68, 86, 173, 90, "bag_s")
    rect(c, 68, 89, 173, 90, "bag_dk")
    rect(c, 0, 90, 68, 92, "chalk")
    return c


def panel_sky():
    """루심을 올려다보는 하늘: 노을 띠, 아래에 해와 관중석 덩어리"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 80, ["dusk1", "dusk2", "dusk3", "dusk4"])
    sun(c, 152, 74, 16)
    crowd(c, 76, 92, 5)
    rect(c, 0, 92, 240, 98, "dirt_dk")
    return c


def panel_wide():
    """운동장 전체, 노을: 위 하늘, 지평선 해, 관중석, 그늘진 외야, 내야 흙 다이아몬드(납작), 왼쪽 아래 덕아웃"""
    c = Cell(260, PANEL_H, 0, 0)
    bands(c, 0, 38, ["dusk1", "dusk2", "dusk3", "dusk4"])
    sun(c, 196, 36, 11)
    crowd(c, 32, 44, 6)
    for x in (30, 130, 230):
        light_pole(c, x, 14, 32)
    rect(c, 0, 44, 260, 48, "wall_s")
    rect(c, 0, 48, 260, 98, "grass_dk")
    c.part(poly([(130, 60), (230, 78), (130, 98), (30, 78)]), "dirt_s", outline=False)
    c.part(poly([(130, 66), (200, 78), (130, 92), (60, 78)]), "grass_dk", outline=False)
    for (x, y) in [(130, 61), (226, 78), (34, 78)]:
        c.part(poly([(x - 3, y), (x, y - 1), (x + 3, y), (x, y + 1)]), "bag", outline=False)
    rect(c, 0, 84, 46, 98, "dug_roof")
    rect(c, 0, 84, 46, 85, "rail_hi")
    return c


def apartments(c, y_base, seed):
    """먼 아파트 단지: 높이가 다른 큰 사각 덩어리 몇 개 (수원 — 번호로 정해지는 높이)"""
    x = 0
    k = 0
    while x < c.w:
        w = 18 + (k * 7 + seed) % 14
        h = 14 + (k * 11 + seed * 3) % 16
        rect(c, x, y_base - h, x + w - 2, y_base, "stand")
        x += w
        k += 1


def panel_school():
    """EP01 새벽의 서림고: 해 뜨는 하늘, 먼 아파트 덩어리, 컨테이너 부실(창 하나에 불), 넓은 흙 운동장"""
    c = Cell(300, PANEL_H, 0, 0)
    bands(c, 0, 58, ["dawn0", "dawn1", "dawn2", "dawn3"])
    sun(c, 214, 54, 14)
    apartments(c, 58, 3)
    rect(c, 0, 58, 300, 98, "dirt_dk")
    rect(c, 0, 58, 300, 59, "dirt_s")
    rect(c, 22, 40, 92, 62, "sil")  # 컨테이너 부실
    rect(c, 22, 40, 92, 41, "sil_rim_hi")
    rect(c, 70, 46, 80, 52, "window")
    for x in range(110, 300, 36):  # 펜스 기둥
        rect(c, x, 44, x + 1, 58, "sil")
    rect(c, 110, 44, 300, 45, "sil")
    return c


def panel_cheongun():
    """EP03 청운고: 3층 숙소 덩어리(불 켜진 창), 큰 전광판, 초록 인조잔디 — 서림고 흙 운동장과 다르게 밝고 넓다"""
    c = Cell(320, PANEL_H, 0, 0)
    bands(c, 0, 52, ["dusk2", "dusk3", "dusk4"])
    sun(c, 160, 50, 12)
    rect(c, 10, 18, 120, 56, "stand_s")  # 숙소
    for fy in (24, 34, 44):
        for fx in range(16, 116, 10):
            rect(c, fx, fy, fx + 5, fy + 4, "window")
    rect(c, 210, 14, 300, 40, "sil")  # 전광판
    rect(c, 214, 18, 296, 36, "stand_s")
    for x in (224, 286):
        rect(c, x, 40, x + 3, 56, "sil")
    rect(c, 0, 56, 320, 98, "grass")
    rect(c, 0, 56, 320, 57, "grass_hi")
    rect(c, 0, 80, 320, 98, "grass_s")
    return c


def panel_sink():
    """EP03 수돗가: 노을 하늘 아래 낮은 담, 긴 콘크리트 수돗가와 수도꼭지 덩어리"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 50, ["dusk2", "dusk3", "dusk4"])
    sun(c, 150, 44, 16)
    rect(c, 0, 40, 240, 52, "concrete_s")
    rect(c, 0, 52, 240, 98, "dirt_dk")
    rect(c, 40, 62, 210, 72, "concrete")  # 수돗가
    rect(c, 40, 72, 210, 98, "concrete_s")
    rect(c, 40, 62, 210, 63, "sil_rim_hi")
    for x in range(56, 200, 28):
        rect(c, x, 56, x + 3, 62, "sil")
        rect(c, x, 56, x + 7, 58, "sil")
    return c


def panel_cage_night():
    """EP07 겨울밤 배팅 케이지: 남색 밤, 왼쪽 위 전구 하나(따뜻한 빛무리), 굵은 그물 줄, 어두운 바닥"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 98, ["night0", "night1", "night2"])
    sun(c, 70, 16, 10)
    for x in range(0, 240, 16):
        rect(c, x, 0, x + 1, 80, "rail")
    for y in range(8, 80, 16):
        rect(c, 0, y, 240, y + 1, "rail")
    rect(c, 0, 80, 240, 98, "sil")
    rect(c, 0, 80, 240, 81, "rail_hi")
    return c


def panel_clubroom_night():
    """EP07 밤의 컨테이너 부실: 밤하늘, 컨테이너 덩어리에 환한 창 하나 (창 안 사람은 인물 칸 soyul_window)"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 98, ["night0", "night1", "night2"])
    for i in range(14):  # 별 몇 개 (번호로 정해지는 자리)
        dot(c, (i * 53) % 240, 4 + (i * 29) % 30, "chalk")
    rect(c, 30, 30, 210, 86, "sil")
    rect(c, 30, 30, 210, 31, "rail_hi")
    rect(c, 96, 42, 146, 72, "window")  # 창 (사람 칸의 발 자리 = 창틀 72)
    rect(c, 0, 86, 240, 98, "dirt_dk")
    return c


def panel_stands():
    """EP08 서호대 1루 관중석: 노을, 큰 계단 덩어리(앉은 자리 76), 오른쪽 아래로 운동장이 조금"""
    c = Cell(260, PANEL_H, 0, 0)
    bands(c, 0, 60, ["dusk1", "dusk2", "dusk3", "dusk4"])
    sun(c, 200, 52, 14)
    for i, y in enumerate(range(56, 98, 10)):
        rect(c, 0, y, 260, y + 10, "stand" if i % 2 == 0 else "stand_s")
        rect(c, 0, y, 260, y + 1, "sil_rim")
    return c


def panel_museum():
    """하린 미술관: 왼쪽 큰 창의 빛, 어두운 벽에 큰 그림 두 점(색 덩어리만), 가운데 긴 의자, 어두운 바닥"""
    c = Cell(300, PANEL_H, 0, 0)
    rect(c, 0, 0, 300, 98, "museum_wall")
    rect(c, 6, 6, 70, 80, "window")
    rect(c, 36, 6, 38, 80, "museum_wall")
    rect(c, 6, 42, 70, 44, "museum_wall")
    for x0, col in ((110, "frame_a"), (200, "frame_b")):
        rect(c, x0, 18, x0 + 70, 58, "sil")
        rect(c, x0 + 4, 22, x0 + 66, 54, col)
        rect(c, x0 + 4, 40, x0 + 66, 54, "dusk2" if col == "frame_a" else "dusk4")
    rect(c, 0, 80, 300, 98, "floor")
    c.put(c._mask(poly([(6, 80), (70, 80), (110, 98), (20, 98)])), PAL["dusk1"])  # 창빛이 바닥에
    rect(c, 140, 74, 230, 78, "sil")  # 긴 의자
    rect(c, 146, 78, 150, 88, "sil")
    rect(c, 220, 78, 224, 88, "sil")
    return c


def panel_clubroom_in():
    """소율 부실 안: 어두운 컨테이너 벽, 책상 위 노트북 화면 하나가 빛 (폴더 칸들은 큰 사각형만)"""
    c = Cell(240, PANEL_H, 0, 0)
    bands(c, 0, 98, ["night1", "night2", "museum_wall"])
    rect(c, 80, 22, 160, 66, "screen_s")
    rect(c, 82, 24, 158, 64, "screen")
    for i in range(4):
        rect(c, 88 + i * 17, 30, 100 + i * 17, 40, "screen_s")
    rect(c, 88, 52, 152, 56, "screen_s")
    rect(c, 88, 52, 130, 56, "sil")  # 영상 진행 막대
    rect(c, 0, 66, 240, 98, "dug_floor")
    rect(c, 0, 66, 240, 67, "rail_hi")
    return c


# ---------- 인물 칸 (손으로 찍은 격자 — scripts/cutscene_grids.py) ----------

def draw_rail(i):
    """덕아웃 난간 + 앞벽 (rise 앞에 놓는 소품): 위에 빛 받은 손잡이 한 줄, 아래 어두운 벽. 칸 셋을 이어 붙여 판 넓이를 덮는다"""
    c = cell()
    rect(c, 0, 58, CELL_W, 96, "dug_roof")
    rect(c, 0, 58, CELL_W, 62, "rail")
    rect(c, 0, 58, CELL_W, 59, "sil_rim_hi")
    for x in (10, 58):
        rect(c, x, 62, x + 3, 96, "rail")
    return c


def grid_row(*names):
    return lambda i: stamp_grid(names[i])


PANELS = [("dugout", panel_dugout), ("rail", panel_rail), ("plate", panel_plate), ("baseline", panel_baseline),
          ("bag", panel_bag), ("sky", panel_sky), ("wide", panel_wide),
          ("school", panel_school), ("cheongun", panel_cheongun), ("sink", panel_sink), ("cage_night", panel_cage_night),
          ("clubroom_night", panel_clubroom_night), ("stands", panel_stands), ("museum", panel_museum), ("clubroom_in", panel_clubroom_in)]
# 인물 줄: (이름, 그리는 함수, 장 수). 장은 왼쪽부터
SPRITES = [("coach", grid_row("COACH"), 1), ("bench", grid_row("BENCH_SIT", "BENCH_STAND"), 2), ("rise", grid_row("RISE"), 1),
           ("rail", draw_rail, 1), ("batter", grid_row("BAT_READY", "BAT_HIT"), 2), ("run", grid_row("RUN_A", "RUN_B"), 2),
           ("helmet", grid_row("HELMET"), 1), ("foot", grid_row("FOOT"), 1), ("mitt", grid_row("MITT"), 1),
           ("ump", grid_row("UMP_WATCH", "UMP_OUT"), 2), ("tiny", grid_row("TINY_STAND", "TINY_BOW"), 2),
           ("tiny_helmet", grid_row("TINY_HELMET"), 1),
           # 에피소드별 컷신 (2026-11-05)
           ("tiny_walk", grid_row("TINY_STAND", "TINY_WALK"), 2), ("coach_front", grid_row("COACH_CROSS"), 1),
           ("sink", grid_row("TAEO_SINK_A", "TAEO_SINK_B"), 2), ("pitcher", grid_row("PITCHER_SET", "PITCHER_KICK"), 2),
           ("toss", grid_row("TOSS"), 1), ("pivot", grid_row("PIVOT"), 1), ("ball", grid_row("BALL"), 1),
           ("soyul_window", grid_row("SOYUL_WINDOW"), 1), ("scouts", grid_row("SCOUTS_SIT", "SCOUTS_UP"), 2),
           ("pair_stand", grid_row("PAIR_STAND"), 1), ("pair_sit", grid_row("PAIR_SIT"), 1)]


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
    print("판 %d장 · 인물 %d줄 · 인물 칸 %d장" % (len(panels), len(SPRITES), sum(n for _, _, n in SPRITES)))


if __name__ == "__main__":
    main()
