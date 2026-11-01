#!/usr/bin/env python3
"""미니게임 도트 (2026-11-01): 입부 첫날 체력 테스트 세 종목의 배경과 인물 칸을 코드로 찍어 만든다.
AI 생성 그림을 쓰지 않는다. 테두리·그늘 규칙과 색·인물 뼈대는 make_pixel_art.py · make_training_art.py 와 같다.

배경 (minigame_bg.png, 한 장 BG_W×BG_H, 줄 = 배경):
    run_far   학교 운동장 먼 풍경 (하늘·교사·나무·그물) — 가로로 이어 붙여 흘린다 (BG_W 주기)
    run_near  트랙 (위쪽은 투명) — 가로로 이어 붙여 흘린다. 레인 줄에 5m(50칸)마다 눈금
    weight    체육관 웨이트장 (거울·스쿼트 랙·안전 바)
    react     실내 연습장 그물 + 오른쪽 신호등 기둥
인물 칸 (minigame.png, 한 칸 CELL_W×CELL_H, 발 자리 FOOT, 줄 = 동작 묶음, 왼쪽부터 칸):
    runner  나: 출발 자세 · 달리기 4장 · 헛발 · 결승 (만세)
    buddy   동기 (흰 연습복): 출발 자세 · 달리기 4장 · 결승
    lifter  나 + 바벨: 서기 · 1/3 · 2/3 · 앉기 · 버티기(땀) · 실패(바가 안전 바에) · 내쉬며 일어섬(입김)
    batter  나: 준비 · 스윙(맞는 순간) · 팔로스루 · 참기(배트 멈춤)
    lamp    신호등 머리: 꺼짐 · 초록 · 빨강
    coach   감독 (왼쪽을 본다): 서 있기 · 수첩에 적기
    lines   출발선 · 결승선
주인공은 얼굴을 그리지 않는다 (모자 챙 그늘).

    python3 scripts/make_minigame_art.py   (pillow·numpy 필요)

나오는 것: godot/art/pixel/minigame_bg.png, godot/art/pixel/minigame.png, godot/scripts/characters/minigame_meta.gd
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from make_pixel_art import PAL, Cell, capsule, circle, ellipse, poly, lerp, hx, OUT  # noqa: E402
import make_training_art as tr  # noqa: E402  (PAL 에 연습복·체육관 색을 더한다)
from make_training_art import guy, standing  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
META = os.path.join(ROOT, "godot", "scripts", "characters", "minigame_meta.gd")

BG_W, BG_H = 200, 90
CELL_W, CELL_H = 56, 72
FOOT = (28, tr.FLOOR)  # 칸 안 발 자리 — standing() 의 바닥(FLOOR)과 같다
X = 28  # 칸 안 인물 x

PAL.update({k: hx(v) for k, v in {
    "school": "#e8dcc6", "school_s": "#cdbd9f", "window": "#7fb4d8", "window_s": "#5d8fb5", "roof": "#b4553f",
    "tree": "#3f9a52", "tree_s": "#2e7a3f", "tree_hi": "#68c070", "fence": "#4c6a5a",
    "white_tee": "#f2f2f6", "white_tee_s": "#c3c6d4",
    "coach_suit": "#26324a", "coach_suit_s": "#18203a", "coach_cap": "#20232f", "coach_cap_s": "#14161f",
    "board": "#c79a62", "paper": "#ffffff",
    "rack": "#a03c3c", "rack_s": "#702828",
    "lamp_off": "#3a3f55", "lamp_green": "#3ad46a", "lamp_green_hi": "#b9ffd0", "lamp_red": "#ff4d5e", "lamp_red_hi": "#ffd0d4",
    "glow_green": "#7ef0a0", "glow_red": "#ff9aa4", "puff": "#e8f2ff", "sweat": "#bcd3e0",
    "check_dk": "#20232f", "sky0": "#7fc0ea", "sky1": "#8ec9ee",  "sky2": "#97ccef",
    "tree_dk": "#1f5a2c", "rack_hi": "#c86060", "rack_dk": "#4a1818",
    "machine_hi": "#c4cbdb", "machine_dk": "#4a5068", "iron_dk": "#161924",
}.items()})


def rect(c, x0, y0, x1, y1, color, alpha=255):
    x0, y0, x1, y1 = (int(round(v)) for v in (x0, y0, x1, y1))
    c.rgba[max(0, y0):min(c.h, y1), max(0, x0):min(c.w, x1)] = (*PAL[color], alpha)


def swap(colors, fn):
    """잠깐 팔레트를 바꿔 그린다 (동기·감독 옷)"""
    old = {k: PAL[k] for k in colors}
    PAL.update({k: PAL[v] for k, v in colors.items()})
    try:
        fn()
    finally:
        PAL.update(old)


# ---------- 배경 ----------

def bg_run_far():
    c = Cell(BG_W, BG_H, 0, 0)
    for i, col in enumerate(["sky0", "sky1", "sky2", "sky"]):
        rect(c, 0, i * 5, BG_W, i * 5 + 5, col)
    rect(c, 0, 20, BG_W, 54, "sky")
    # 교사 (가로로 길게, 창문 20칸 주기)
    rect(c, 0, 16, BG_W, 17, "roof")
    rect(c, 0, 17, BG_W, 46, "school")
    rect(c, 0, 44, BG_W, 46, "school_s")
    for x in range(4, BG_W, 20):
        for y in (21, 32):
            rect(c, x, y, x + 12, y + 7, "window")
            rect(c, x, y + 5, x + 12, y + 7, "window_s")
            rect(c, x + 6, y, x + 7, y + 7, "school_s")
    # 나무 (40칸 주기)
    for base in range(0, BG_W, 40):
        c.ramp(circle((base + 14, 44), 8), "tree", 2)
        c.ramp(circle((base + 24, 46), 7), "tree", 2)
    # 운동장 그물 울타리
    rect(c, 0, 48, BG_W, 56, "fence")
    for x in range(0, BG_W, 4):
        rect(c, x, 48, x + 1, 56, "tree_s")
    rect(c, 0, 48, BG_W, 49, "iron_s")
    rect(c, 0, 56, BG_W, BG_H, "grass")
    return c


def bg_run_near():
    c = Cell(BG_W, BG_H, 0, 0)
    rect(c, 0, 56, BG_W, 60, "grass")
    rect(c, 0, 58, BG_W, 60, "turf_s")
    rect(c, 0, 60, BG_W, BG_H, "track")
    for y in range(64, BG_H, 6):  # 트랙 결
        for x in range((y * 7) % 9, BG_W, 9):
            rect(c, x, y, x + 2, y + 1, "track_s")
    for y in (61, 75, 89):  # 레인 줄
        rect(c, 0, y, BG_W, y + 1, "line")
    for x in range(0, BG_W, 50):  # 5m 눈금
        rect(c, x, 62, x + 1, 64, "line")
        rect(c, x, 76, x + 1, 78, "line")
    return c


def bg_weight():
    c = Cell(BG_W, BG_H, 0, 0)
    rect(c, 0, 0, BG_W, 76, "gym_wall")
    for y in range(8, 76, 10):
        rect(c, 0, y, BG_W, y + 1, "gym_wall_s")
    rect(c, 10, 12, 60, 56, "mirror")
    rect(c, 10, 12, 60, 13, "iron_s")
    for d in range(0, 30, 2):  # 거울 반짝임 사선
        rect(c, 20 + d, 16 + d, 21 + d, 17 + d, "paper")
    # 덤벨 거치대 (오른쪽)
    rect(c, 150, 50, 190, 52, "iron_s")
    rect(c, 152, 52, 154, 76, "iron_s")
    rect(c, 186, 52, 188, 76, "iron_s")
    for j, dx in enumerate(range(155, 186, 7)):
        c.part(capsule((dx, 47), (dx + 4, 47), 4 - (j % 2)), "iron", "iron_s")
    # 스쿼트 랙: 기둥 둘 + 안전 바
    for px in (78, 120):
        c.ramp(poly([(px, 14), (px + 4, 14), (px + 4, 80), (px, 80)]), "rack", 1)
        for y in range(20, 76, 6):
            rect(c, px + 1, y, px + 3, y + 1, "rack_s")
    c.ramp(poly([(70, 65), (130, 65), (130, 67), (70, 67)]), "iron", 1)
    rect(c, 0, 76, BG_W, BG_H, "gym_floor")
    rect(c, 64, 79, 136, 82, "mat")
    return c


def bg_react():
    c = Cell(BG_W, BG_H, 0, 0)
    rect(c, 0, 0, BG_W, 76, "net")
    for x in range(0, BG_W, 6):
        rect(c, x, 0, x + 1, 76, "net_line")
    for y in range(2, 76, 6):
        rect(c, 0, y, BG_W, y + 1, "net_line")
    rect(c, 0, 76, BG_W, BG_H, "turf")
    rect(c, 0, 76, BG_W, 77, "turf_s")
    # 타석 흰 줄
    rect(c, 54, 82, 92, 83, "line")
    # 신호등 기둥 (머리는 lamp 칸이 얹힌다)
    c.ramp(poly([(158, 30), (162, 30), (162, 80), (158, 80)]), "machine", 1)
    c.part(poly([(150, 78), (170, 78), (170, 82), (150, 82)]), "machine", "machine_s")
    return c


# ---------- 인물 칸 ----------

def cell():
    return Cell(CELL_W, CELL_H, 0, 0)


RUN = [  # make_training_art.scene_speed 와 같은 뛰기 4장 (x 기준만 옮김)
    lambda x: dict(fk=(x + 7, 54), fft=(x + 12, 60), bk=(x - 4, 59), bft=(x - 10, 60), fe=(x - 3, 40), fh=(x - 6, 36), be=(x + 5, 40), bh=(x + 9, 36)),
    lambda x: dict(fk=(x + 4, 57), fft=(x + 3, tr.FLOOR - 1), bk=(x - 2, 54), bft=(x - 8, 55), fe=(x - 2, 41), fh=(x - 1, 46), be=(x + 3, 41), bh=(x + 5, 46)),
    lambda x: dict(fk=(x - 4, 59), fft=(x - 10, 60), bk=(x + 7, 54), bft=(x + 12, 60), fe=(x + 5, 40), fh=(x + 9, 36), be=(x - 3, 40), bh=(x - 6, 36)),
    lambda x: dict(fk=(x - 2, 54), fft=(x - 8, 55), bk=(x + 4, 57), bft=(x + 3, tr.FLOOR - 1), fe=(x + 3, 41), fh=(x + 5, 46), be=(x - 2, 41), bh=(x - 1, 46)),
]


def runner_pose(i, x=X):
    """0 출발 · 1~4 뛰기 · 5 헛발 · 6 결승"""
    if i == 0:  # 스탠딩 스타트: 앞으로 숙이고 앞발에 무게
        return standing(x, head=(x + 7, 31), sh=(x + 4, 38), hip=(x - 1, 50), fk=(x + 5, 57), fft=(x + 6, tr.FLOOR - 1),
                        bk=(x - 6, 58), bft=(x - 10, tr.FLOOR - 1), fe=(x - 2, 44), fh=(x - 5, 48), be=(x + 7, 44), bh=(x + 9, 49))
    if 1 <= i <= 4:
        bob = [0, 1, 0, 1][i - 1]
        return standing(x, head=(x + 4, 26 + bob), sh=(x + 2, 34 + bob), hip=(x, 48 + bob), **RUN[i - 1](x))
    if i == 5:  # 헛발: 앞으로 고꾸라질 듯, 팔을 휘젓는다
        return standing(x, head=(x + 10, 33), sh=(x + 6, 39), hip=(x, 50), fk=(x + 6, 58), fft=(x + 9, tr.FLOOR - 1),
                        bk=(x - 5, 56), bft=(x - 12, 58), fe=(x + 10, 34), fh=(x + 14, 28), be=(x + 1, 30), bh=(x - 2, 23))
    return standing(x, fe=(x + 3, 28), fh=(x + 5, 20), be=(x - 3, 28), bh=(x - 5, 20))  # 만세


def draw_runner(i):
    c = cell()
    guy(c, runner_pose(i))
    if 1 <= i <= 4:  # 바람 줄
        for j, y in enumerate((30, 40, 50)):
            rect(c, 2 + (i * 3 + j * 5) % 6, y, 9 + (i * 3 + j * 5) % 6, y + 1, "line")
    return c


def draw_buddy(i):
    c = cell()
    frame = [0, 1, 2, 3, 4, 6][i]
    swap({"shirt": "white_tee", "shirt_s": "white_tee_s"}, lambda: guy(c, runner_pose(frame)))
    return c


def lifter_pose(depth, x=X):
    def at(a, b):
        return lerp(a, b, depth)
    head = at((x + 1, 25), (x + 4, 34))
    sh = at((x, 34), (x + 2, 43))
    hip = at((x - 1, 49), (x - 7, 56))
    knee = at((x + 2, 57), (x + 7, 57))
    k = standing(x, head=head, sh=sh, hip=hip, bk=knee, fk=(knee[0] + 1, knee[1]),
                 bft=(x - 1, tr.FLOOR - 1), fft=(x + 1, tr.FLOOR - 1),
                 be=(sh[0] - 5, sh[1] + 2), bh=(sh[0] - 3, sh[1] - 2), fe=(sh[0] - 4, sh[1] + 3), fh=(sh[0] - 2, sh[1] - 2))
    return k, head, sh


def draw_lifter(i):
    """0~3 깊이 0 · ⅓ · ⅔ · 1, 4 버티기, 5 실패, 6 내쉬며 일어섬"""
    c = cell()
    depth = [0.0, 1 / 3, 2 / 3, 1.0, 0.85, 1.0, 0.0][i]
    k, head, sh = lifter_pose(depth)
    if i == 5:
        # 실패: 바는 안전 바 높이(배경 랙의 안전 바와 같은 y)에 내려앉고 손은 무릎에
        c.part(circle((X - 6, 52), 7), "iron", "iron_s")
        c.part(circle((X - 6, 52), 2), "iron_hi")
        k.update(fe=(X + 6, 50), fh=(X + 8, 56), be=(X + 3, 50), bh=(X + 6, 56))
        guy(c, k)
        return c
    plate = (sh[0] - 7, sh[1] - 3)
    c.part(circle(plate, 7), "iron", "iron_s")
    c.part(circle(plate, 2), "iron_hi")
    guy(c, k)
    if i in (3, 4):  # 땀
        c.part(ellipse((head[0] + 8, head[1] - 6), 1, 1.5), "sweat")
    if i == 4:
        c.part(ellipse((head[0] - 6, head[1] - 7), 1, 1.5), "sweat")
        for d in (-1, 1):  # 떨림 줄
            rect(c, plate[0] - 10 + d, plate[1] - 1, plate[0] - 9 + d, plate[1] + 2, "line")
    if i == 6:  # 입김
        for j, (dx, dy, r) in enumerate([(9, 2, 1.6), (13, 1, 2.2), (18, 0, 2.6)]):
            c.part(circle((head[0] + dx, head[1] + dy), r), "puff", outline=False)
    return c


def draw_batter(i):
    """0 준비 · 1 맞는 순간 · 2 팔로스루 · 3 참기"""
    c = cell()
    x = X - 2
    poses = [
        standing(x, be=(x - 6, 36), bh=(x - 4, 30), fe=(x - 2, 38), fh=(x - 4, 30), bat=((x - 4, 30), -115)),
        standing(x + 1, head=(x + 3, 26), sh=(x + 1, 35), fk=(x + 6, 57), fft=(x + 8, tr.FLOOR - 1), be=(x + 1, 42), bh=(x + 7, 45), fe=(x + 5, 40), fh=(x + 7, 45), bat=((x + 7, 45), 2)),
        standing(x + 1, head=(x + 3, 26), sh=(x + 1, 35), fk=(x + 6, 57), fft=(x + 8, tr.FLOOR - 1), bk=(x - 1, 58), bft=(x - 3, tr.FLOOR - 2),
                 be=(x + 5, 37), bh=(x + 3, 30), fe=(x + 6, 35), fh=(x + 3, 30), bat=((x + 3, 30), -165)),
        standing(x, fk=(x + 5, 57), fft=(x + 7, tr.FLOOR - 1), be=(x - 3, 40), bh=(x + 1, 38), fe=(x, 41), fh=(x + 1, 38), bat=((x + 1, 38), -70)),
    ]
    guy(c, poses[i])
    if i == 1:
        for d in [(-3, -4), (4, -5), (5, 2), (-2, 4)]:  # 휘두른 자리 파편
            c.put(c._mask(circle((x + 28 + d[0], 44 + d[1]), 0.6)), PAL["line"])
    return c


def draw_lamp(i):
    """신호등 머리: 칸 아래 가운데(발 자리)가 기둥 꼭대기에 얹힌다"""
    c = cell()
    cx, top = FOOT[0], FOOT[1] - 24
    c.ramp(poly([(cx - 9, top), (cx + 9, top), (cx + 9, top + 24), (cx - 9, top + 24)]), "iron", 1)
    color = ["lamp_off", "lamp_green", "lamp_red"][i]
    if i > 0:
        glow = ["", "glow_green", "glow_red"][i]
        c.part(circle((cx, top + 12), 10), glow, outline=False)
        c.ramp(poly([(cx - 9, top), (cx + 9, top), (cx + 9, top + 24), (cx - 9, top + 24)]), "iron", 1)
    c.part(circle((cx, top + 12), 6), color)
    if i > 0:
        c.part(circle((cx - 2, top + 10), 1.5), color + "_hi", outline=False)
    rect(c, cx - 10, top - 2, cx + 10, top, "iron_s")  # 차양
    return c


def draw_coach(i):
    c = cell()

    def body():
        k = standing(X, fe=(X + 4, 40), fh=(X + 7, 40), be=(X - 1, 41), bh=(X + 4, 41) if i == 1 else (X - 1, 48))
        guy(c, k)
        # 수첩: 앞손에 든 판
        c.part(poly([(X + 5, 34), (X + 11, 34), (X + 11, 43), (X + 5, 43)]), "board")
        c.part(poly([(X + 6, 35), (X + 10, 35), (X + 10, 42), (X + 6, 42)]), "paper", outline=False)
        if i == 1:
            rect(c, X + 7, 37, X + 10, 38, "ink")
            rect(c, X + 7, 39, X + 9, 40, "ink")
    swap({"shirt": "coach_suit", "shirt_s": "coach_suit_s", "pants": "coach_suit", "pants_s": "coach_suit_s",
          "cap": "coach_cap", "cap_s": "coach_cap_s"}, body)
    c.rgba = np.ascontiguousarray(c.rgba[:, ::-1])  # 왼쪽을 본다 (도트는 돌리지 않고 좌우만 뒤집는다)
    return c


def draw_lines(i):
    """출발선 · 결승선: 발 자리가 트랙 바깥 레인 줄(배경 y 89)에 놓인다. 위쪽 트랙 줄(배경 y 61)까지 이어진다"""
    c = cell()
    x = FOOT[0]
    y0, y1 = FOOT[1] - 28, FOOT[1]
    if i == 0:
        rect(c, x - 1, y0, x + 1, y1, "line")
        return c
    for j, y in enumerate(range(y0, y1, 2)):
        for dx in (0, 2):
            rect(c, x - 2 + dx, y, x + dx, y + 2, "line" if (j + dx // 2) % 2 == 0 else "check_dk")
    for px in (x - 4, x + 4):  # 기둥 둘 + 테이프
        c.ramp(poly([(px - 1, y0 - 26), (px + 1, y0 - 26), (px + 1, y1), (px - 1, y1)]), "machine", 1)
    rect(c, x - 4, y0 - 22, x + 5, y0 - 21, "cone")
    return c


BGS = [("run_far", bg_run_far), ("run_near", bg_run_near), ("weight", bg_weight), ("react", bg_react)]
SPRITES = [
    ("runner", draw_runner, 7),
    ("buddy", draw_buddy, 6),
    ("lifter", draw_lifter, 7),
    ("batter", draw_batter, 4),
    ("lamp", draw_lamp, 3),
    ("coach", draw_coach, 2),
    ("lines", draw_lines, 2),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    bg = Image.new("RGBA", (BG_W, BG_H * len(BGS)))
    for row, (_, fn) in enumerate(BGS):
        bg.paste(fn().image(), (0, row * BG_H))
    bg.save(os.path.join(OUT, "minigame_bg.png"))
    cols = max(n for _, _, n in SPRITES)
    sheet = Image.new("RGBA", (CELL_W * cols, CELL_H * len(SPRITES)))
    for row, (_, fn, n) in enumerate(SPRITES):
        for i in range(n):
            sheet.paste(fn(i).image(), (i * CELL_W, row * CELL_H))
    sheet.save(os.path.join(OUT, "minigame.png"))
    lines = [
        "class_name MinigameMeta",
        "extends RefCounted",
        "## scripts/make_minigame_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.",
        "## art/pixel/minigame_bg.png: 줄 = 배경 · art/pixel/minigame.png: 줄 = 동작 묶음, 칸 = 장",
        "",
        "const BG_SIZE := Vector2(%d, %d)" % (BG_W, BG_H),
        "const BG_ROWS := {%s}" % ", ".join('"%s": %d' % (n, i) for i, (n, _) in enumerate(BGS)),
        "const CELL := Vector2(%d, %d)" % (CELL_W, CELL_H),
        "## 칸 안 발 자리 (인물을 놓을 때 이 점을 바닥에 맞춘다)",
        "const FOOT := Vector2(%d, %d)" % FOOT,
        "## 이름 -> [줄, 장 수]",
        "const ROWS := {%s}" % ", ".join('"%s": [%d, %d]' % (n, i, k) for i, (n, _, k) in enumerate(SPRITES)),
        "",
    ]
    with open(META, "w") as f:
        f.write("\n".join(lines))
    # 확인용 크게 본 그림 (godot/build/shots/)
    shots = os.path.join(ROOT, "godot", "build", "shots")
    os.makedirs(shots, exist_ok=True)
    sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST).save(os.path.join(shots, "pixel-minigame.png"))
    bg.resize((bg.width * 3, bg.height * 3), Image.NEAREST).save(os.path.join(shots, "pixel-minigame-bg.png"))
    print("minigame_bg.png %d×%d, minigame.png %d×%d" % (bg.width, bg.height, sheet.width, sheet.height))


if __name__ == "__main__":
    main()
