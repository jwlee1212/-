#!/usr/bin/env python3
"""도트 시험판 (2026-10-30): 타석 화면의 도트 그림을 코드로 찍어 만든다. AI 생성 그림을 쓰지 않는다.

캐릭터는 관절 좌표(머리·어깨·팔꿈치·손·무릎·발·배트 각도)로 포즈를 적고, 그 사이를 보간해 장 수를 늘린다.
부위마다 1픽셀 남색 테두리를 두르고, 오른쪽 아래 가장자리에 그늘을 넣는다 (빛은 왼쪽 위).
같은 함수가 모든 포즈를 그리니 포즈끼리 크기·색·얼굴이 어긋나지 않는다.

    python3 scripts/make_pixel_art.py      (pillow·numpy 필요)

나오는 것 (godot/art/pixel/):
    field.png    경기장 배경 (340×130, 화면에서 3배)
    batter.png   타자 뒷모습 시트 (칸마다 한 포즈)
    pitcher.png  투수 앞모습 시트
    catcher.png  포수 뒷모습
    mitt.png     포수 미트
그리고 godot/scripts/characters/pixel_meta.gd — 칸 크기·발 자리·포즈 순서·공이 출발하는 손 자리 (Godot 가 읽는다).
좌표는 모두 "발 자리 기준, 위가 -" 인 그림 픽셀.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "godot", "art", "pixel")
META = os.path.join(ROOT, "godot", "scripts", "characters", "pixel_meta.gd")

# 화면 1칸 = 그림 1픽셀 × P (논리 좌표 844×390 기준)
P = 3
SCREEN_W, SCREEN_H = 844, 390


def hx(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


PAL = {k: hx(v) for k, v in {
    "ink": "#1e2240",
    "skin": "#f6c7a0", "skin_s": "#d8946c",
    # 우리 팀 (타자): 파랑
    "us": "#2f62e0", "us_s": "#1d3d9e", "us_hi": "#8fb0ff", "us_dk": "#152c70", "hair": "#3b2a22",
    "jersey": "#eef0f6", "jersey_s": "#b4bbcc",
    "shoe": "#2a2b38", "shoe_s": "#15161e",
    "bat": "#dca060", "bat_s": "#9c6232",
    "belt": "#2a2b38",
    # 상대 팀 (투수·포수): 빨강
    "them": "#e5404e", "them_s": "#a42638", "them_hi": "#ff8f99",
    "gear": "#2c3046", "gear_s": "#181a28",
    "glove": "#b0703a", "glove_s": "#6e3f18", "pocket": "#7a4720",
    "ball": "#ffffff", "ball_s": "#c9ced8",
    "eye": "#1e2240",
}.items()}


# ---------- 그리기 바탕 ----------

def shifted(m, dx, dy):
    """r[y, x] = m[y + dy, x + dx] (밖이면 False)"""
    h, w = m.shape
    r = np.zeros_like(m)
    ys = slice(max(0, -dy), min(h, h - dy))
    xs = slice(max(0, -dx), min(w, w - dx))
    ys2 = slice(max(0, dy), min(h, h + dy))
    xs2 = slice(max(0, dx), min(w, w + dx))
    r[ys, xs] = m[ys2, xs2]
    return r


def grow(m):
    return m | shifted(m, 1, 0) | shifted(m, -1, 0) | shifted(m, 0, 1) | shifted(m, 0, -1)


class Cell:
    """포즈 한 칸. (ax, ay) 가 발 자리"""

    def __init__(self, w, h, ax, ay):
        self.w, self.h, self.ax, self.ay = w, h, ax, ay
        self.rgba = np.zeros((h, w, 4), np.uint8)

    def _mask(self, fn):
        """fn 하나 또는 여러 개 (여러 개면 합쳐 한 덩어리 — 이음새에 안쪽 선이 안 생긴다)"""
        im = Image.new("L", (self.w, self.h), 0)
        for f in (fn if isinstance(fn, (list, tuple)) else [fn]):
            f(ImageDraw.Draw(im), lambda p: (p[0] + self.ax, p[1] + self.ay))
        return np.array(im) > 127

    def put(self, m, color, alpha=255):
        self.rgba[m] = (*color, alpha)

    def part(self, fn, color, shade=None, d=1, outline=True):
        """부위 하나: 테두리(밑에 깔린 부위 위에도 그어 안쪽 선이 된다) → 색 → 오른쪽 아래 그늘"""
        m = self._mask(fn)
        if not m.any():
            return m
        if outline:
            self.put(grow(m) & ~m, PAL["ink"])
        self.put(m, PAL[color])
        if shade:
            self.put(m & ~shifted(m, d, d), PAL[shade])
        return m

    def shadow(self, rx, ry):
        m = self._mask(lambda d, t: d.ellipse(_box(t((0, 0)), rx, ry), fill=255))
        self.put(m, PAL["ink"], 70)

    def image(self):
        return Image.fromarray(self.rgba)


def _box(c, rx, ry):
    return [c[0] - rx, c[1] - ry, c[0] + rx, c[1] + ry]


def circle(c, r):
    return lambda d, t: d.ellipse(_box(t(c), r, r), fill=255)


def ellipse(c, rx, ry):
    return lambda d, t: d.ellipse(_box(t(c), rx, ry), fill=255)


def capsule(a, b, w):
    def fn(d, t):
        d.line([t(a), t(b)], fill=255, width=int(w))
        r = (w - 1) / 2.0
        for p in (a, b):
            d.ellipse(_box(t(p), r, r), fill=255)
    return fn


def poly(pts):
    return lambda d, t: d.polygon([t(p) for p in pts], fill=255)


def add(a, b):
    return (a[0] + b[0], a[1] + b[1])


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def along(p, ang, length):
    r = math.radians(ang)
    return (p[0] + math.cos(r) * length, p[1] + math.sin(r) * length)


def blend(k0, k1, t):
    """포즈 둘 사이 (관절은 직선 보간, 배트 각도는 짧은 쪽으로 돈다)"""
    out = {}
    for k, v in k0.items():
        w = k1.get(k, v)
        if isinstance(v, tuple):
            out[k] = lerp(v, w, t)
        elif k.endswith("ang"):
            diff = (w - v + 180) % 360 - 180
            out[k] = v + diff * t
        elif isinstance(v, (int, float)) and not isinstance(v, bool):
            out[k] = v + (w - v) * t
        else:
            out[k] = v if t < 0.5 else w
    return out


def ease(t):
    return t * t * (3 - 2 * t)


# ---------- 타자 (뒷모습, 오른손 타자) ----------
# 카메라는 타자 뒤 가슴 높이, 타자는 화면 왼쪽 가운데에 크게 선다. 몸은 3/4 로 돌아 있어 등(그림 왼쪽)과
# 오른쪽 옆구리·오른쪽 귀(그림 오른쪽)가 같이 보인다. 손은 뒤쪽 어깨(그림 오른쪽) 위 귀 높이, 배트는 비스듬히 세운다.

BATTER_CELL = (150, 128, 62, 122)

# 화면 배치 (batting_view.gd 와 같게 맞춘다): 타자 발 BATTER_X·BATTER_FEET_Y, 스트라이크 존 가운데 ZONE_Y, 존 반높이
LAYOUT = dict(batter_x=0.34, batter_feet_y=0.97, zone_y=0.70, zone_half_h=0.095)
# 맞는 자리: 존 가운데가 타자 발에서 몇 그림 픽셀 떨어져 있나
CONTACT = ((0.5 - LAYOUT["batter_x"]) * SCREEN_W / P, (LAYOUT["zone_y"] - LAYOUT["batter_feet_y"]) * SCREEN_H / P)
ZONE_HALF_H = LAYOUT["zone_half_h"] * SCREEN_H / P

# 등번호 7 (3×5)
SEVEN = ["###", "..#", ".#.", ".#.", ".#."]


# ---------- 손으로 찍은 판 (몸통·헬멧) ----------
# 도형으로 그리면 몸통이 상자, 헬멧이 공처럼 보여서, 이 둘은 한 픽셀씩 찍은 판을 관절 자리에 찍는다.
# 글자: . 투명  K 테두리  B 헬멧  b 헬멧 그늘  d 헬멧 아래 테  h 헬멧 빛  W 유니폼  w 유니폼 그늘  n 등번호
#       L 벨트  S 피부  s 피부 그늘  r 머리카락
STAMP_COLORS = {
    "K": "ink", "B": "us", "b": "us_s", "d": "us_dk", "h": "us_hi", "W": "jersey", "w": "jersey_s", "n": "us",
    "L": "belt", "S": "skin", "s": "skin_s", "r": "hair",
}

# 몸통 (3/4 뒷모습): 왼쪽은 등, 오른쪽 띠는 그늘진 옆구리. 앵커 = 두 어깨 가운데 (13, 2)
TORSO = [
    "......KKKKKKKKKKKKKK......",
    "...KKKWWWWWWWWWWWWWWKKK...",
    ".KKWWWWWWWWWWWWWWWWWWWwKK.",
    "KWWWWWWWWWWWWWWWWWWWWWwwwK",
    "KWWWWWWWWWWWWWWWWWWWWWWwwK",
    "KwWWWWWWWWWWWWWWWWWWWWWwwK",
    "KwWWWnnnnWWWWWWWWWWWWWwwwK",
    ".KwWWWWWnnWWWWWWWWWWWWwwK.",
    ".KwWWWWWnWWWWWWWWWWWWWwwK.",
    ".KwWWWWnnWWWWWWWWWWWWWwwK.",
    ".KwWWWWnWWWWWWWWWWWWWwwwK.",
    "..KwWWWnWWWWWWWWWWWWWwwK..",
    "..KwWWWWWWWWWWWWWWWWWwwK..",
    "..KwWWWWWWWWWWWWWWWWWwwK..",
    "..KwwWWWWWWWWWWWWWWWwwwK..",
    "..KwwWWWWWWWWWWWWWWWwwwK..",
    "...KwwWWWWWWWWWWWWWwwwK...",
    "...KLLLLLLLLLLLLLLLLLLK...",
    "...KLLLLLLLLLLLLLLLLLLK...",
    "...KWWWWWWWWWWWWWWWWwwK...",
    "...KWWWWWWWWWWWWWWWwwwK...",
]
TORSO_ANCHOR = (13, 2)
TORSO_L_SH, TORSO_R_SH = (3, 4), (23, 4)
# 다리가 붙는 자리: 포수 뒤에서 보면 몸이 옆으로 서서 두 다리가 앞뒤로 겹친다 — 앞다리(왼쪽)는 멀어서 조금 오른쪽,
# 뒷다리(오른쪽)는 카메라 쪽이라 앞에 그려 앞다리를 거의 가린다
TORSO_L_HIP, TORSO_R_HIP = (15, 19), (10, 20)

# 몸통 (비스듬한 옆모습 — 준비~랜딩): 몸이 옆으로 서서 앞 어깨가 투수를 향하고, 꼬임 때문에 가슴이 포수 쪽으로 조금
# 틀어져 있다. 그래서 포수 뒤에서는 등이 아니라 등 가장자리(왼쪽 그늘) → 옆구리 솔기(세로 선) → 가슴 앞판(단추 줄)이
# 보이고, 등번호는 안 보인다. 다리가 앞뒤로 겹치는 것과 맞는 각도다.
# 회전하면서 가슴이 투수 쪽으로 돌면 등이 넓게 보이는 TORSO(3/4 뒷모습)로 바뀐다. 앵커 = 어깨 가운데 (11, 2)
TORSO_SIDE = [
    "......KKKKKKKKKKK.......",
    "...KKKWWWWWWWWWWWKK.....",
    "..KwWWWWWWWWWWWWWWWKK...",
    ".KwwWWWWWWWWWWWWWWWWWK..",
    "KwwWWWWWWwWWWWWWwWWWwwK.",
    "KwwWWWWWWwWWWWWWwWWWwwK.",
    "KwwWWWWWWwWWWWWWKWWWwwwK",
    "KwwWWWWWWwWWWWWWwWWWwwwK",
    "KwwWWWWWWwWWWWWWwWWwwwK.",
    ".KwwWWWWWwWWWWWWKWWwwK..",
    ".KwwWWWWWwWWWWWWwWWwwK..",
    ".KwwWWWWWwWWWWWWwWwwK...",
    ".KwwWWWWWwWWWWWWKWwwK...",
    ".KwwWWWWWwWWWWWWwwwK....",
    ".KwwWWWWWwWWWWWWwwwK....",
    "..KwwWWWWwWWWWWwwwK.....",
    "..KwwWWWWwWWWWwwwwK.....",
    "..KLLLLLLLLLLLLLLLK.....",
    "..KLLLLLLLLLLLLLLLK.....",
    "..KwWWWWWWWWWWWwwwK.....",
    "..KwWWWWWWWWWWwwwK......",
]
TORSOS = {
    # [판, 앵커, 왼어깨(먼 쪽), 오른어깨(카메라 쪽), 앞다리 엉덩이, 뒷다리 엉덩이]
    "back": [TORSO, TORSO_ANCHOR, TORSO_L_SH, TORSO_R_SH, TORSO_L_HIP, TORSO_R_HIP],
    "side": [TORSO_SIDE, (11, 2), (14, 3), (15, 4), (13, 19), (8, 20)],
}

# 헬멧 (3/4 뒷모습): 둥근 뒤통수, 오른쪽에 챙 끝이 살짝 나오고, 왼쪽 아래로 귀덮개(투수 쪽 귀),
# 오른쪽 아래로 맨 귀, 아래에 머리카락과 목. 앵커 = 헬멧 가운데 (14, 12)
HELMET = [
    "..........KKKKKKKK",
    ".......KKKBBBBBBBBKKK",
    ".....KKBBBBBBBBBBBBBBKK",
    "....KBBBhhhhBBBBBBBBBBBK",
    "...KBBBhhhhhhBBBBBBBBBBbK",
    "..KBBBBhhhhhBBBBBBBBBBBbbK",
    "..KBBBBBhhhBBBBBBBBBBBBBbK",
    ".KBBBBBBBBBBBBBBBBBBBBBBbbK",
    ".KBBBBBBBBBBBBBBBBBBBBBBBbK",
    ".KBBBBBBBBBBBBBBBBBBBBBBBbbK",
    "KBBBBBBBBBBBBBBBBBBBBBBBBbbK",
    "KBBBBBBBBBBBBBBBBBBBBBBBBbbKKK",
    "KBBBBBBBBBBBBBBBBBBBBBBBbbbbdK",
    "KbBBBBBBBBBBBBBBBBBBBBBbbbKKKK",
    "KbbBBBBBBBBBBBBBBBBBBBbbbbK",
    "KdbbbBBBBBBBBBBBBBBBbbbbbK",
    ".KddbbbbbbbbbbbbbbbbbbbdKK",
    ".KBdddddddddddddddddddKSSK",
    ".KBBbKrrrrrrrrrrrrrrKKSSsK",
    ".KBBbKrrrrrrrrrrrrrrrKSssK",
    ".KBbbKKSSSSSSSSSSSSSSKsK",
    "..KbbKSSSSSSSSSSSSSSSsK",
    "..KKKKSSSSSSSSSSSSSssK",
    "......KKKKKKKKKKKKKK",
]
HELMET_ANCHOR = (14, 12)

# 헬멧 (몸통과 같이 포수 쪽으로 틀어진 머리 — 준비~랜딩): 뒤통수는 같고, 귀덮개(먼 쪽 귀)는 안 보이며,
# 오른쪽에 챙 끝이 나오고 그 아래로 옆얼굴이 살짝 보인다 — 챙 그늘에 들어가 눈코입은 안 보이고 귀와 뺨 윤곽만. 앵커는 HELMET 과 같다
HELMET_SIDE = [
    "..........KKKKKKKK",
    ".......KKKBBBBBBBBKKK",
    ".....KKBBBBBBBBBBBBBBKK",
    "....KBBBhhhhBBBBBBBBBBBK",
    "...KBBBhhhhhhBBBBBBBBBBbK",
    "..KBBBBhhhhhBBBBBBBBBBBbbK",
    "..KBBBBBhhhBBBBBBBBBBBBBbK",
    ".KBBBBBBBBBBBBBBBBBBBBBBbbK",
    ".KBBBBBBBBBBBBBBBBBBBBBBBbK",
    ".KBBBBBBBBBBBBBBBBBBBBBBBbbK",
    "KBBBBBBBBBBBBBBBBBBBBBBBBbbKK",
    "KBBBBBBBBBBBBBBBBBBBBBBBBbbddKKK",
    "KBBBBBBBBBBBBBBBBBBBBBBBbbbdddddK",
    "KbBBBBBBBBBBBBBBBBBBBBbbbbKKKKKK",
    "KbbBBBBBBBBBBBBBBBBBbbbbKssK",
    "KdbbbBBBBBBBBBBBBBbbbbbKsssK",
    ".KddbbbbbbbbbbbbbbbbbbKsssK",
    ".KrddddddddddddddddddKSsssK",
    ".KrrrrrrrrrrrrrrrrrrKSSssK",
    ".KrrrrrrrrrrrrrrrrrrKSsssK",
    "..KrrrrrrrrrrrrrrrrrKSssK",
    "..KSSSSSSSSSSSSSSSSSSssK",
    "...KsSSSSSSSSSSSSSSsssK",
    "....KKKKKKKKKKKKKKKKKK",
]
HELMETS = {"back": HELMET, "side": HELMET_SIDE}


def stamp(c, grid, at, anchor):
    """판을 찍는다: 판의 anchor 칸이 at(발 기준 좌표)에 온다"""
    ox = int(round(at[0])) - anchor[0] + c.ax
    oy = int(round(at[1])) - anchor[1] + c.ay
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != ".":
                c.rgba[oy + y, ox + x] = (*PAL[STAMP_COLORS[ch]], 255)
    return (ox - c.ax, oy - c.ay)


# 배트 굵기 (손잡이 끝에서 몇 %, 반폭 픽셀): 손잡이 끝 노브 → 가는 손잡이 → 천천히 굵어짐 → 굵은 몸통 → 둥근 끝
BAT_PROFILE = [(0.0, 1.5), (0.05, 0.9), (0.38, 0.9), (0.62, 1.7), (0.8, 2.3), (1.0, 2.4)]


def bat_shape(hands, ang, length):
    """배트 한 덩어리 (테두리가 하나로 이어진다). 손 쪽 끝은 손에 가려지므로 손보다 조금 뒤에서 시작한다"""
    r = math.radians(ang)
    d = (math.cos(r), math.sin(r))
    n = (-d[1], d[0])
    start = (hands[0] - d[0] * 3, hands[1] - d[1] * 3)
    total = length + 3

    def at(t, side):
        w = BAT_PROFILE[0][1]
        for (t0, w0), (t1, w1) in zip(BAT_PROFILE, BAT_PROFILE[1:]):
            if t0 <= t <= t1:
                w = w0 + (w1 - w0) * (t - t0) / (t1 - t0)
        p = (start[0] + d[0] * total * t, start[1] + d[1] * total * t)
        return (p[0] + n[0] * w * side, p[1] + n[1] * w * side)

    ts = [i / 20 for i in range(21)]
    outline = [at(t, 1) for t in ts] + [at(t, -1) for t in reversed(ts)]
    tip = (start[0] + d[0] * total, start[1] + d[1] * total)
    return [poly(outline), circle(tip, 2.4), circle(start, 1.5)]


def draw_batter(k):
    c = Cell(*BATTER_CELL)
    c.shadow(14, 4)
    # 몸통 판은 두 어깨 가운데에 찍는다. 다리·팔이 붙는 자리(엉덩이·어깨)는 판에서 나온다
    # 몸통 판은 다리 각도에 맞춘다: 준비~랜딩은 옆모습(side), 회전부터는 3/4 뒷모습(back)
    torso, anchor, t_l_sh, t_r_sh, t_l_hip, t_r_hip = TORSOS[k.get("torso", "back")]
    sh_mid = lerp(k["l_sh"], k["r_sh"], 0.5)
    top_left = (int(round(sh_mid[0])) - anchor[0], int(round(sh_mid[1])) - anchor[1])
    at = lambda p: (top_left[0] + p[0], top_left[1] + p[1])
    l_hip, r_hip = at(t_l_hip), at(t_r_hip)
    sh_l, sh_r = at(t_l_sh), at(t_r_sh)

    # 그립: 오른손 타자는 왼손이 아래(손잡이 끝 쪽), 오른손이 그 위(배트 몸통 쪽)에 붙는다
    grip = along((0, 0), k["bat_ang"], 1.7)
    low_hand = (k["hands"][0] - grip[0], k["hands"][1] - grip[1])
    top_hand = (k["hands"][0] + grip[0], k["hands"][1] + grip[1])

    def bat():
        c.part(bat_shape(low_hand, k["bat_ang"], k["bat_len"] + 1.7), "bat", "bat_s")

    def left_arm():
        c.part([capsule(sh_l, k["l_elbow"], 6), capsule(k["l_elbow"], low_hand, 5)], "us", "us_s")

    if not k["bat_front"]:
        bat()
    if not k["l_arm_front"]:
        left_arm()
    # 다리: 먼 다리(왼쪽)부터. 허벅지·정강이(바지, 한 덩어리) → 양말 → 신발
    for side in ("l", "r"):
        h0 = l_hip if side == "l" else r_hip
        knee, foot = k[side + "_knee"], k[side + "_foot"]
        ankle = add(foot, (0, -3))
        c.part([capsule(add(h0, (0, -2)), knee, 10), capsule(knee, lerp(knee, ankle, 0.62), 8)], "jersey", "jersey_s", 2)
        c.part(capsule(lerp(knee, ankle, 0.62), ankle, 6), "us", "us_s")
        c.part(ellipse(add(foot, (1, -1)), 5, 3), "shoe", "shoe_s")
    stamp(c, torso, sh_mid, anchor)
    # 오른팔 (카메라 쪽 뒷팔): 반팔 소매 → 파란 언더셔츠
    elbow = k["r_elbow"]
    # 위팔과 아래팔을 한 덩어리로 그려 팔꿈치에 이음새 선이 생기지 않게 한다 (굵기만 위팔 6 → 아래팔 5 로 가늘어짐)
    c.part([capsule(sh_r, elbow, 6), capsule(elbow, top_hand, 5)], "us", "us_s")
    c.part(capsule(sh_r, lerp(sh_r, elbow, 0.4), 8), "jersey", "jersey_s")
    if k["l_arm_front"]:
        left_arm()
    stamp(c, HELMETS[k.get("torso", "back")], k["head"], HELMET_ANCHOR)
    if k["bat_front"]:
        bat()
    # 장갑 낀 두 손은 늘 맨 앞 (배트가 헬멧 뒤로 지나가도 손은 보인다): 왼손(아래) → 오른손(위, 앞에 겹침)
    # 장갑은 흰색 — 파란 언더셔츠 팔과 같은 색이면 팔꿈치·팔뚝·손이 한 덩어리로 뭉쳐 손이 여러 개처럼 보인다
    c.part(circle(low_hand, 2.2), "jersey", "jersey_s")
    c.part(circle(top_hand, 2.2), "jersey", "jersey_s")
    return c


def bat_to(hands, target):
    dx, dy = target[0] - hands[0], target[1] - hands[1]
    return math.degrees(math.atan2(dy, dx)), math.hypot(dx, dy)


B = {}
# 3/4 시점: 등과 오른쪽 옆구리가 같이 보인다. 두 발은 거의 같은 높이에 옆으로 벌려 둘 다 땅을 딛는다.
# 순서: 스탠스 → 로드 → 스트라이드 → 랜딩 → 회전 → 컨택 → 팔로스루
# 1. 스탠스: 무릎을 굽히고 윗몸을 홈플레이트 쪽으로 살짝 숙인다
B["stance"] = dict(
    hip=(-1, -30), l_sh=(-8, -49), r_sh=(12, -47), head=(2, -63),
    l_knee=(-12, -16), l_foot=(-12, -3), r_knee=(10, -15), r_foot=(11, 0),
    r_elbow=(23, -46), l_elbow=(6, -44), hands=(16, -54),
    bat_ang=-132.0, bat_len=47.0, bat_front=False, l_arm_front=False)
B["breath"] = dict(B["stance"], l_sh=(-8, -48), r_sh=(12, -46), head=(2, -62), hands=(16, -53), r_elbow=(23, -45))
# 2. 로드: 무게를 뒷다리로 싣고 앞 무릎을 안으로 감는다(앞발 뒤꿈치가 들림). 손은 뒤·위로 당긴다
B["load"] = dict(B["stance"],
    l_sh=(-7, -49), r_sh=(13, -48), head=(3, -63),
    l_knee=(-9, -16), l_foot=(-11, -4), r_knee=(11, -15),
    r_elbow=(24, -49), hands=(17, -57), bat_ang=-138.0)
# 3. 스트라이드: 앞발을 살짝 들었다가(무릎 아래로 발) 투수 쪽으로 뻗는다. 손은 뒤에 남는다(꼬임)
B["stride_up"] = dict(B["load"],
    l_knee=(-8, -21), l_foot=(-10, -10), r_knee=(11, -16))
B["stride"] = dict(B["load"],
    l_sh=(-8, -48), r_sh=(12, -47), head=(2, -62),
    l_knee=(-14, -17), l_foot=(-17, -6), r_knee=(11, -15),
    hands=(18, -57), r_elbow=(25, -49), bat_ang=-140.0)
# 4. 랜딩: 앞발이 땅을 딛고 폭이 넓어진다. 몸이 한 칸 가라앉고, 손은 가장 뒤(발사 자세), 배트는 더 눕는다
B["landing"] = dict(B["stride"],
    l_sh=(-8, -47), r_sh=(12, -46), head=(2, -61),
    l_knee=(-15, -14), l_foot=(-17, -3), r_knee=(11, -14),
    hands=(18, -55), r_elbow=(25, -47), bat_ang=-145.0)
# 5. 회전: 엉덩이가 먼저 열리고(뒷발 뒤꿈치가 들림) 손이 어깨로 내려오며 배트가 눕는다 →
#    배트가 몸 앞(투수 쪽)으로 돌아 나와 짧아 보인다
B["rotate1"] = dict(B["landing"],
    l_sh=(-7, -47), r_sh=(13, -45), head=(4, -61),
    r_knee=(9, -14), r_foot=(10, -1),
    r_elbow=(24, -43), hands=(19, -50), bat_ang=-168.0, bat_len=40.0)
B["rotate2"] = dict(B["rotate1"],
    l_sh=(-5, -47), r_sh=(14, -45), head=(6, -61),
    r_knee=(7, -14), r_foot=(10, -2),
    r_elbow=(24, -41), hands=(21, -47), bat_ang=-65.0, bat_len=20.0, bat_front=False)


def contact_key(dy):
    """6. 컨택: 공 높이 dy(존 가운데 기준, 아래가 +)에 맞춘다. 낮은 공엔 무릎을 더 굽혀 몸을 낮춘다"""
    crouch = max(dy, 0) * 0.3
    hands = (22, -42 + dy * 0.55 + crouch)
    ang, length = bat_to(hands, (CONTACT[0], CONTACT[1] + dy))
    return dict(B["rotate2"],
        l_sh=(-4, -46 + crouch), r_sh=(15, -44 + crouch), head=(7, -60 + crouch),
        l_knee=(-15, -13 + crouch * 0.5), r_knee=(6, -13 + crouch * 0.5), r_foot=(9, -2),
        r_elbow=(23, -38 + dy * 0.4 + crouch), hands=hands, bat_ang=ang, bat_len=length, bat_front=False)


B["contact_high"] = contact_key(-ZONE_HALF_H)
B["contact_mid"] = contact_key(0)
B["contact_low"] = contact_key(ZONE_HALF_H)
# 7. 팔로스루: 두 팔을 투수 쪽으로 쭉 뻗고(배트가 멀리 가리켜 짧아 보임) → 배트가 카메라 쪽으로 돌아오고 →
#    왼쪽 어깨 위로 감기고 → 등 뒤로 넘어가 멈춘다. 뒷발은 발끝으로 서서 돈다
B["extend"] = dict(B["contact_mid"],
    hands=(23, -47), r_elbow=(25, -42), bat_ang=-30.0, bat_len=24.0, bat_front=False)
B["follow1"] = dict(B["contact_mid"],
    l_sh=(-5, -47), r_sh=(14, -46), head=(6, -61), r_foot=(9, -3),
    hands=(16, -50), r_elbow=(23, -45), bat_ang=100.0, bat_len=18.0, bat_front=True)
B["follow2"] = dict(B["follow1"],
    l_sh=(-7, -49), r_sh=(12, -48), head=(5, -63), r_knee=(6, -14), r_foot=(8, -4),
    hands=(0, -57), r_elbow=(11, -55), l_elbow=(-14, -53), bat_ang=180.0, bat_len=40.0, l_arm_front=True)
B["follow3"] = dict(B["follow2"],
    l_sh=(-8, -50), r_sh=(11, -49), head=(4, -64),
    hands=(-8, -60), r_elbow=(4, -58), l_elbow=(-16, -53), bat_ang=28.0, bat_len=42.0)
B["finish"] = dict(B["follow3"],
    l_sh=(-8, -49), r_sh=(11, -48), head=(4, -63),
    hands=(-9, -58), r_elbow=(3, -56), l_elbow=(-16, -51), bat_ang=40.0)
# 번트: 무릎을 굽혀 낮추고 배트를 눕혀 존 앞에 댄다
B["bunt"] = dict(B["stance"],
    l_sh=(-7, -45), r_sh=(13, -44), head=(5, -59),
    l_knee=(-13, -13), r_knee=(11, -12),
    r_elbow=(20, -40), hands=(16, -46), bat_ang=-8.0, bat_len=40.0, bat_front=False)
# 다리 (2026-10-30 다시 잡음): 포수 뒤 시점에서는 발이 투수 쪽으로 앞뒤 일렬이라 다리가 겹쳐 보인다.
# 뒷발(오른발)이 아래·앞, 앞발(왼발)은 조금 위·오른쪽(멀리). 무릎은 발목 안쪽으로 굽혀 홈플레이트 쪽(오른쪽)으로 나온다.
LEGS = {
    # 스탠스: 어깨보다 조금 넓게, 무게는 뒷다리에 조금 더
    "stance": dict(r_foot=(-2, 0), r_knee=(5, -15), l_foot=(6, -5), l_knee=(12, -17)),
    # 로드: 앞 무릎을 포수 쪽(안쪽)으로 감고 앞발 뒤꿈치를 든다, 뒷다리에 실린다
    "load": dict(r_foot=(-2, 0), r_knee=(5, -15), l_foot=(5, -7), l_knee=(8, -17)),
    # 스트라이드: 앞발을 살짝 들었다가 투수 쪽(멀리 = 위·오른쪽)으로 뻗는다
    "stride_up": dict(r_foot=(-2, 0), r_knee=(5, -15), l_foot=(5, -12), l_knee=(7, -21)),
    "stride": dict(r_foot=(-2, 0), r_knee=(5, -15), l_foot=(9, -9), l_knee=(12, -19)),
    # 랜딩: 앞발이 딛고 무릎을 굽혀 받는다, 뒷무릎도 더 굽어 몸이 가라앉는다
    "landing": dict(r_foot=(-2, 0), r_knee=(6, -14), l_foot=(9, -7), l_knee=(14, -17)),
    # 회전: 뒷무릎이 앞·안쪽으로 몰리고 뒷발 뒤꿈치가 들린다(발끝으로 돈다), 앞다리는 버틴다
    "rotate1": dict(r_foot=(-1, -1), r_knee=(6, -14), l_foot=(9, -7), l_knee=(12, -19)),
    "rotate2": dict(r_foot=(0, -2), r_knee=(7, -13), l_foot=(9, -7), l_knee=(11, -20)),
    "contact": dict(r_foot=(0, -2), r_knee=(8, -13), l_foot=(9, -7), l_knee=(10, -20)),
    # 팔로스루: 뒷발이 발끝으로 서서 다 돌고, 앞다리는 곧게 선다
    "follow": dict(r_foot=(1, -4), r_knee=(8, -15), l_foot=(9, -7), l_knee=(10, -20)),
    "bunt": dict(r_foot=(-2, 0), r_knee=(4, -13), l_foot=(7, -6), l_knee=(12, -16)),
}
for name, legs in (("stance", "stance"), ("breath", "stance"), ("load", "load"), ("stride_up", "stride_up"), ("stride", "stride"),
                   ("landing", "landing"), ("rotate1", "rotate1"), ("rotate2", "rotate2"),
                   ("contact_high", "contact"), ("contact_mid", "contact"), ("contact_low", "contact"),
                   ("extend", "contact"), ("follow1", "follow"), ("follow2", "follow"), ("follow3", "follow"), ("finish", "follow"), ("bunt", "bunt")):
    B[name] = dict(B[name], **LEGS[legs])
# 몸통 각도: 다리가 옆으로 선 동안(스탠스~회전 시작)은 옆모습, 엉덩이·가슴이 투수 쪽으로 돈 뒤(회전 둘째 칸~)는 3/4 뒷모습
for name in ("stance", "breath", "load", "stride_up", "stride", "landing", "rotate1"):
    B[name] = dict(B[name], torso="side")
    # 옆얼굴이 손에 가리지 않게 머리는 조금 왼쪽으로
    if name != "rotate1":
        B[name] = dict(B[name], head=add(B[name]["head"], (-2, 0)))
# 준비~랜딩의 배트: 손은 귀 높이, 배트는 뒤(왼쪽 위)로 낮게 눕혀 헬멧 아래쪽(챙 근처)을 가로지른다. 헬멧 위쪽을
# 가로지르면 배트 위로 잘려 나간 헬멧 조각이 팔처럼 보여서, 둥근 위쪽(빛 포함)이 통째로 보이게 아래로 지나간다.
# 뒷팔 팔꿈치는 어깨보다 조금 아래·바깥으로 내려 위팔(어깨→팔꿈치)과 아래팔(팔꿈치→손)이 V 자로 읽히게 한다.
# 위팔 길이는 약 10칸, 아래팔 약 12칸 — SD 체형이라 몸통(18칸)보다 짧게.
# 단계가 갈수록 조금씩 더 눕는다. [손, 팔꿈치, 배트 각도]
for name, hands, elbow, ang in (("stance", (18, -55), (15, -43), -162.0), ("breath", (18, -54), (15, -42), -162.0),
                                ("load", (19, -57), (16, -45), -165.0), ("stride_up", (19, -57), (16, -45), -166.0),
                                ("stride", (19, -56), (16, -44), -167.0), ("landing", (19, -54), (16, -42), -170.0)):
    B[name] = dict(B[name], hands=hands, r_elbow=elbow, bat_ang=ang, bat_len=46.0, bat_front=True)
BATTER_KEYS = B

# 시트 순서: [이름, 포즈] — 사이 그림은 두 포즈를 보간해 만든다
BATTER_FRAMES = [
    ("stance", B["stance"]),
    ("breath", B["breath"]),
    ("load_a", blend(B["stance"], B["load"], 0.5)),
    ("load", B["load"]),
    ("stride_up", B["stride_up"]),
    ("stride", B["stride"]),
    ("landing", B["landing"]),
    ("rotate1", B["rotate1"]),
    ("rotate2", B["rotate2"]),
    ("contact_high", B["contact_high"]),
    ("contact_mid", B["contact_mid"]),
    ("contact_low", B["contact_low"]),
    ("extend", B["extend"]),
    ("follow1", B["follow1"]),
    ("follow2", B["follow2"]),
    ("follow3", B["follow3"]),
    ("finish", B["finish"]),
    ("bunt", B["bunt"]),
    # 공이 끝난 뒤 멈춰 있던 자세(마무리·랜딩)에서 준비 자세로 돌아가는 사이 칸
    ("recover", blend(B["finish"], B["stance"], 0.5)),
    ("settle", blend(B["landing"], B["stance"], 0.5)),
]


# ---------- 투수 (앞모습, 오른손 투수) ----------
# 타자와 같은 방식: 몸통·머리는 손으로 찍은 판, 팔·다리는 관절. 카메라(타석)를 보고 서 있다가 몸을 돌리므로
# 판은 세 방향 — 정면(front), 옆(side: 3루 쪽 = 그림 왼쪽을 봄), 3/4(threeq: 다시 카메라 쪽으로 열리는 중).
# 정면에서는 던지는 팔(오른팔)이 그림 왼쪽, 글러브 팔(왼팔)이 그림 오른쪽. 옆모습에서는 왼쪽 몸이 카메라 쪽이라
# 던지는 팔이 몸 뒤로 숨는다. 내딛는 발(왼발)은 카메라 쪽으로 오니 마운드보다 조금 아래에 딛는다.
# 순서: 준비 → 와인드업 → 레그리프트 → 스트라이드 → 암 코킹 → 앞발 착지 → 회전·가속 → 릴리스 → 팔로스루

PITCHER_CELL = (44, 40, 22, 36)

# 글자: K 테두리  C 모자  c 모자 그늘(챙)  q 모자 빛  S 피부  s 피부 그늘  r 머리카락  W 유니폼  w 유니폼 그늘  L 벨트
P_COLORS = {"K": "ink", "C": "them", "c": "them_s", "q": "them_hi", "S": "skin", "s": "skin_s", "r": "hair",
            "W": "jersey", "w": "jersey_s", "L": "belt"}

# 머리 (앵커 = 얼굴 가운데 (5, 5)). 얼굴은 4줄: 날카롭게 꺾인 눈썹 → 눈 → 코 그늘 → 다문 입. 모자 챙 그늘이 눈썹·눈 두 줄을 덮는다(눈만 진하게 보임)
P_HEADS = {
    "front": [
        "...KKKKK...",
        "..KCCCCCK..",
        ".KCqqCCCcK.",
        ".KCCCCCCcK.",
        "KcccccccccK",
        "KssKssKsssK",
        "KsKssssKssK",
        "KSSSSsSSSsK",
        ".KSSssSSsK.",
        "..KKKKKKK..",
    ],
    # 그림 왼쪽(3루 쪽)을 보는 옆얼굴: 챙이 왼쪽으로, 뒤통수에 머리카락
    "side": [
        "....KKKKK..",
        "...KCCCCCK.",
        "..KCqqCCCcK",
        "..KCCCCCCcK",
        "KccccccCCcK",
        ".KsKKssKrrK",
        "KssKssssrrK",
        "KSSSSSSSsrK",
        ".KsSSSSsK..",
        "..KKKKKK...",
    ],
    # 카메라 쪽으로 돌아오는 중: 눈이 왼쪽으로 쏠림
    "threeq": [
        "...KKKKK...",
        "..KCCCCCK..",
        ".KCqqCCCcK.",
        ".KCCCCCCcK.",
        "KcccccccccK",
        "KssKssssssK",
        "KsKssKssssK",
        "KSSsSSSSssK",
        ".KSssSSssK.",
        "..KKKKKKK..",
    ],
}
P_HEAD_ANCHOR = (5, 5)

# 몸통: [판, 앵커(어깨 가운데), 던지는 어깨, 글러브 어깨, 디딤발 엉덩이, 내딛는 발 엉덩이]
P_TORSOS = {
    "front": [[
        "..KKKKKK..",
        ".KWWWWWWK.",
        "KWWWWWWWwK",
        "KWWWWWWWwK",
        "KWWWWWWWwK",
        ".KWWWWWwK.",
        ".KLLLLLLK.",
        ".KWWWWWwK.",
    ], (5, 1), (1, 2), (8, 2), (3, 7), (6, 7)],
    "side": [[
        ".KKKKK.",
        "KWWWWWK",
        "KWWWWwK",
        "KWWWWwK",
        "KWWWWwK",
        ".KWWWwK",
        ".KLLLLK",
        ".KWWWwK",
    ], (3, 1), (4, 2), (2, 2), (4, 7), (3, 7)],
    "threeq": [[
        ".KKKKKK..",
        "KWWWWWWK.",
        "KWWWWWWwK",
        "KWWWWWWwK",
        "KWWWWWWwK",
        ".KWWWWwK.",
        ".KLLLLLK.",
        ".KWWWWwK.",
    ], (4, 1), (1, 2), (6, 2), (3, 7), (5, 7)],
}


def draw_pitcher(k):
    c = Cell(*PITCHER_CELL)
    c.shadow(7, 1.5)
    grid, anchor, t_sh, g_sh, p_hip, g_hip = P_TORSOS[k["body"]]
    top_left = (int(round(k["chest"][0])) - anchor[0], int(round(k["chest"][1])) - anchor[1])
    at = lambda p: (top_left[0] + p[0], top_left[1] + p[1])
    t_sh, g_sh, p_hip, g_hip = at(t_sh), at(g_sh), at(p_hip), at(g_hip)

    def stamp_p(rows, pos, anc):
        ox = int(round(pos[0])) - anc[0] + c.ax
        oy = int(round(pos[1])) - anc[1] + c.ay
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    c.rgba[oy + y, ox + x] = (*PAL[P_COLORS[ch]], 255)

    def throw_arm():
        c.part([capsule(t_sh, k["t_elbow"], 2), capsule(k["t_elbow"], k["t_hand"], 2)], "them", "them_s")
        c.part(circle(k["t_hand"], 1.2), "skin", outline=False)
        if k["ball"]:
            c.part(circle(add(k["t_hand"], (0, -1)), 1.2), "ball", "ball_s")

    def glove_arm():
        c.part([capsule(g_sh, k["g_elbow"], 2), capsule(k["g_elbow"], k["g_hand"], 2)], "them", "them_s")
        c.part(ellipse(k["g_hand"], 2.2, 2.6), "glove", "glove_s")

    def leg(hip, knee, foot):
        ankle = add(foot, (0, -1.5))
        c.part([capsule(hip, knee, 3), capsule(knee, lerp(knee, ankle, 0.5), 3)], "jersey", "jersey_s")
        c.part(capsule(lerp(knee, ankle, 0.5), ankle, 2), "them", "them_s")
        c.part(ellipse(foot, 2, 1), "shoe", outline=False)

    if not k["t_front"]:
        throw_arm()
    if not k["g_front"]:
        glove_arm()
    # 디딤발(오른다리) → 내딛는 다리(왼다리, 카메라 쪽이라 앞에). 팔로스루에서 축 다리가 앞다리 앞을 지나 넘어갈 땐 반대로
    if not k.get("p_front"):
        leg(p_hip, k["p_knee"], k["p_foot"])
    if not k.get("knee_up"):
        leg(g_hip, k["g_knee"], k["g_foot"])
    if k.get("p_front"):
        leg(p_hip, k["p_knee"], k["p_foot"])
    stamp_p(grid, k["chest"], anchor)
    if k.get("knee_up"):
        # 레그리프트: 든 무릎이 몸 앞(카메라 쪽)으로 나와 몸통을 가린다
        leg(g_hip, k["g_knee"], k["g_foot"])
    stamp_p(P_HEADS[k["face"]], k["head"], P_HEAD_ANCHOR)
    if k["g_front"]:
        glove_arm()
    if k["t_front"]:
        throw_arm()
    return c


PITCHER_KEYS = {}
PKEY = PITCHER_KEYS
# 1. 준비: 카메라를 보고 서서 글러브와 공을 가슴 앞에 모은다
PKEY["set"] = dict(body="front", face="front", chest=(0, -15), head=(0, -20),
                p_knee=(-2, -5), p_foot=(-2, 0), g_knee=(2, -5), g_foot=(2, 0),
                t_elbow=(-4, -11), t_hand=(-1, -11), g_elbow=(4, -11), g_hand=(1, -11),
                ball=False, t_front=False, g_front=True)
# 2. 와인드업: 두 손을 모은 채 모자 위로 들어 올린다
PKEY["windup"] = dict(PKEY["set"], chest=(0, -15), head=(0, -20),
                   t_elbow=(-5, -19), t_hand=(-1, -25), g_elbow=(5, -19), g_hand=(0, -25))
# 3. 레그리프트: 몸을 옆으로 돌려(3루 쪽을 봄) 왼무릎을 가슴 높이로 든다. 손은 가슴 앞으로 내려온다
PKEY["lift"] = dict(PKEY["set"], body="side", face="side", chest=(0, -16), head=(0, -21),
                 p_knee=(0, -5), p_foot=(0, 0), g_knee=(-6, -11), g_foot=(-4, -6),
                 t_elbow=(1, -12), t_hand=(0, -13), g_elbow=(-2, -12), g_hand=(0, -13), t_front=False, g_front=True, knee_up=True)
# 레그리프트로 가는 길: 몸을 옆으로 돌리며 손을 가슴으로 내리고 무릎이 올라오기 시작한다
PKEY["lift_start"] = dict(PKEY["lift"], g_knee=(-3, -8), g_foot=(-2, -3), t_hand=(0, -14), g_hand=(0, -14), g_elbow=(-2, -12), t_elbow=(1, -12))
# 4. 스트라이드: 왼발을 카메라 쪽으로 뻗어 내리고(마운드보다 조금 아래), 손이 갈라진다 — 글러브는 앞으로, 공은 몸 뒤 아래로
PKEY["stride"] = dict(PKEY["lift"], knee_up=False, chest=(-1, -14), head=(-1, -19),
                   p_knee=(1, -5), p_foot=(1, 0), g_knee=(-4, -5), g_foot=(-4, 1),
                   t_elbow=(3, -11), t_hand=(4, -8), g_elbow=(-5, -13), g_hand=(-7, -13), ball=True)
# 5. 암 코킹: 몸이 카메라 쪽으로 열리기 시작(3/4), 던지는 팔을 들어 팔꿈치는 어깨 높이, 공은 머리 위 뒤쪽
PKEY["cock"] = dict(PKEY["stride"], body="threeq", face="threeq", chest=(-1, -14), head=(-1, -19),
                 g_knee=(-4, -5), g_foot=(-4, 2),
                 t_elbow=(-7, -15), t_hand=(-7, -21), g_elbow=(4, -14), g_hand=(6, -14), t_front=False, g_front=True)
# 6. 앞발 착지: 왼발이 땅을 딛고 무릎을 굽혀 받는다, 몸이 낮아지고 팔은 가장 뒤로 젖혀진다
PKEY["land"] = dict(PKEY["cock"], chest=(-2, -13), head=(-2, -18),
                 g_knee=(-4, -5), g_foot=(-4, 2), p_knee=(1, -4), p_foot=(2, 0),
                 t_elbow=(-8, -15), t_hand=(-9, -19), g_elbow=(3, -13), g_hand=(4, -12))
# 7. 회전·가속: 가슴이 카메라 쪽으로 돌고(정면), 팔꿈치가 앞장서 팔이 머리 위로 넘어온다. 뒷발은 끌린다
PKEY["rotate"] = dict(PKEY["land"], body="front", face="front", chest=(-2, -13), head=(-2, -18),
                   p_knee=(0, -4), p_foot=(1, -1),
                   t_elbow=(-6, -18), t_hand=(-5, -23), g_elbow=(2, -12), g_hand=(2, -11), t_front=True)
# 8. 릴리스: 몸이 카메라 쪽으로 쏠리고, 팔을 앞·위로 쭉 뻗어 공을 놓는다. 글러브는 가슴에 붙인다
PKEY["release"] = dict(PKEY["rotate"], chest=(-3, -12), head=(-3, -17),
                    g_knee=(-3, -5), g_foot=(-4, 2), p_knee=(-1, -5), p_foot=(0, -2),
                    t_elbow=(-8, -16), t_hand=(-7, -21), g_elbow=(1, -11), g_hand=(1, -10), ball=False)
# 9. 팔로스루: 허리를 숙이며 던진 팔이 몸 앞을 가로질러 반대쪽 무릎 옆으로, 뒷다리가 뒤로 차올라간다
#    축 다리(오른다리)는 뒤로 차오른 뒤 앞다리 앞을 지나 반대편(1루 쪽 = 그림 오른쪽)으로 넘어가 딛고, 수비 자세로 선다
PKEY["kick"] = dict(PKEY["release"], chest=(-3, -11), head=(-3, -16), ball=False,
                    g_knee=(-2, -4), g_foot=(-4, 2), p_knee=(-7, -6), p_foot=(-9, -6),
                    t_elbow=(-3, -8), t_hand=(-1, -5), g_elbow=(2, -10), g_hand=(3, -9))
PKEY["follow"] = dict(PKEY["kick"], chest=(-2, -10), head=(-2, -15),
                      p_knee=(-1, -7), p_foot=(1, -6), p_front=True,
                      t_elbow=(-1, -7), t_hand=(2, -4))
PKEY["settle"] = dict(PKEY["follow"], chest=(0, -13), head=(0, -18),
                      g_knee=(-3, -5), g_foot=(-4, 1), p_knee=(3, -5), p_foot=(4, 1),
                      t_elbow=(-3, -10), t_hand=(-1, -9), g_elbow=(4, -11), g_hand=(2, -11))

# [이름, 포즈, 시작] — 시작은 와인드업 진행률(0~1, 1 이 공을 놓는 순간) 또는 "+ms"(공을 놓은 뒤 ms)
PK = PITCHER_KEYS
# 시간 배분 (와인드업 windupMs 1250 기준): 준비 ~ 레그리프트 정점 0.75초 → 정점 ~ 앞발 착지 0.38초 →
# 착지 ~ 릴리스 0.12초 → 릴리스 ~ 팔로스루 마무리 0.3초. 모두 합쳐 약 1.55초
PITCHER_FRAMES = [
    ("set", PK["set"], 0.0),
    ("windup_a", blend(PK["set"], PK["windup"], 0.5), 0.07),
    ("windup", PK["windup"], 0.14),
    ("lift_a", PK["lift_start"], 0.3),
    ("lift", PK["lift"], 0.5),           # 정점 0.6 전후로 머문다
    ("stride_a", blend(PK["lift"], PK["stride"], 0.5), 0.66),
    ("stride", PK["stride"], 0.75),
    ("cock", PK["cock"], 0.83),
    ("land", PK["land"], 0.9),           # 앞발 착지
    ("rotate", PK["rotate"], 0.94),
    ("release", PK["release"], 0.975),   # 공은 1.0 에 손을 떠난다
    ("whip", PK["kick"], "+50"),
    ("follow", PK["follow"], "+110"),
    ("settle_a", blend(PK["follow"], PK["settle"], 0.5), "+200"),
    ("settle", PK["settle"], "+300"),
]


# ---------- 포수 (뒷모습, 쪼그려 앉음) · 미트 ----------

CATCHER_CELL = (64, 44, 32, 44)


def draw_catcher():
    c = Cell(*CATCHER_CELL)
    # 무릎 (양옆으로 벌어진 다리) → 등 → 장비 끈 → 오른팔 → 헬멧(거꾸로 쓴 마스크 끈)
    for sx in (-1, 1):
        c.part(ellipse((sx * 15, -4), 8, 7), "jersey", "jersey_s", 2)
        c.part(ellipse((sx * 19, -1), 4, 6), "gear", "gear_s")
    c.part(ellipse((0, -14), 15, 13), "them", "them_s", 3)
    m = c._mask(lambda d, t: (d.line([t((-11, -24)), t((10, -6))], fill=255, width=2), d.line([t((11, -24)), t((-10, -6))], fill=255, width=2)))
    c.put(m, PAL["gear"])
    c.part(capsule((13, -20), (18, -8), 5), "them", "them_s")
    c.part(circle((0, -30), 9), "gear", "gear_s", 2)
    c.part(capsule((-9, -31), (9, -31), 2), "gear_s", outline=False)
    c.part(ellipse((-4, -35), 2.5, 1.5), "jersey_s", outline=False)
    return c


def draw_mitt():
    c = Cell(13, 13, 6, 6)
    c.part(ellipse((0, 0), 5, 5.5), "glove", "glove_s", 2)
    c.part(ellipse((0, 0.5), 2.5, 3), "pocket", outline=False)
    for x in (-3, 0, 3):
        c.put(c._mask(lambda d, t, x=x: d.line([t((x, -5)), t((x, -3))], fill=255)), PAL["glove_s"])
    return c


# ---------- 경기장 배경 ----------

BG_W, BG_H = 340, 130
# batting_view.gd 의 HORIZON_Y·MOUND_Y 와 같게
HORIZON_Y = 0.46
MOUND_Y = 0.56


def make_field():
    """화면 가운데에 놓는다. 논리 좌표 → 그림: x = (lx - 422) / 3 + 170, y = ly / 3"""
    im = Image.new("RGB", (BG_W, BG_H))
    d = ImageDraw.Draw(im)
    rng = np.random.default_rng(7)

    def X(fx):
        return (fx * SCREEN_W - SCREEN_W / 2) / P + BG_W / 2

    def Y(fy):
        return fy * BG_H

    sh = HORIZON_Y - 0.40  # 관중석·전광판을 지평선과 함께 내린다
    horizon = round(Y(HORIZON_Y))
    # 하늘: 띠 네 개, 띠 경계는 바둑판 디더
    sky = [hx("#3a72cf"), hx("#4f88dc"), hx("#6aa0e6"), hx("#8cbaee")]
    band = horizon / len(sky)
    for y in range(horizon):
        i = min(int(y / band), len(sky) - 1)
        for x in range(BG_W):
            j = i
            if i + 1 < len(sky) and y >= (i + 1) * band - 1 and (x + y) % 2 == 0:
                j = i + 1
            im.putpixel((x, y), sky[j])
    # 구름 몇 덩이
    for cx, cy, s in ((40, 9, 1.0), (118, 6, 0.8), (250, 10, 1.1), (318, 5, 0.7)):
        for dx, dy, r in ((0, 0, 4), (5, 1, 3), (-5, 1, 3), (2, -2, 3)):
            d.ellipse(_box((cx + dx * s, cy + dy * s), r * s, r * s * 0.7), fill=hx("#eef5ff"))
        d.line([(cx - 7 * s, cy + 3 * s), (cx + 8 * s, cy + 3 * s)], fill=hx("#cfe0f6"))
    # 관중석: 윗층(어두움) → 아랫층, 관중 점
    d.rectangle([0, Y(0.235 + sh), BG_W, Y(0.32 + sh)], fill=hx("#3d4a6e"))
    d.rectangle([0, Y(0.31 + sh), BG_W, horizon], fill=hx("#7f93ba"))
    d.line([(0, Y(0.31 + sh)), (BG_W, Y(0.31 + sh))], fill=hx("#2a3352"))
    crowd = [hx(h) for h in ("#ffffff", "#ffd84a", "#ff7a6a", "#7fd1ff", "#ffb0d0", "#c6f08a", "#2a3352")]
    for y in range(int(Y(0.245 + sh)), horizon - 1, 2):
        for x in range(BG_W):
            if rng.random() < 0.55:
                col = crowd[rng.integers(len(crowd))]
                im.putpixel((x, y), col)
                if rng.random() < 0.5 and y + 1 < horizon:
                    im.putpixel((x, y + 1), tuple(int(v * 0.7) for v in col))
    # 조명탑
    for fx in (0.16, 0.84):
        x = round(X(fx))
        d.rectangle([x - 1, Y(0.17 + sh), x, horizon], fill=hx("#2a3352"))
        d.rectangle([x - 8, Y(0.12 + sh), x + 7, Y(0.19 + sh)], fill=hx("#2a3352"), outline=hx("#1e2240"))
        for i in range(4):
            for j in range(2):
                d.rectangle([x - 7 + i * 4, Y(0.12 + sh) + 2 + j * 4, x - 6 + i * 4, Y(0.12 + sh) + 3 + j * 4], fill=hx("#fff6c0"))
    # 전광판: 줄 점수판 모양 (칸과 노란 숫자 점)
    bx0, bx1, by0, by1 = round(X(0.38)), round(X(0.62)), round(Y(0.12 + sh)), round(Y(0.32 + sh))
    d.rectangle([bx0, by0, bx1, by1], fill=hx("#232a40"), outline=hx("#1e2240"))
    d.rectangle([bx0 + 3, by0 + 3, bx1 - 3, by0 + 10], fill=hx("#2f6b4a"))
    for i in range(9):
        x = bx0 + 6 + i * ((bx1 - bx0 - 12) / 9)
        for row in range(2):
            y = by0 + 14 + row * 6
            d.rectangle([x, y, x + 3, y + 3], fill=hx("#11141f"))
            if rng.random() < 0.7:
                d.point((x + 1 + int(rng.integers(2)), y + 1 + int(rng.integers(2))), fill=hx("#ffc21a"))
    for i in range(3):
        d.rectangle([bx0 + 5 + i * 5, by1 - 6, bx0 + 7 + i * 5, by1 - 4], fill=hx("#22b573"))
        d.rectangle([bx1 - 19 + i * 5, by1 - 6, bx1 - 17 + i * 5, by1 - 4], fill=hx("#ff4d5e"))
    # 외야 담장 + 노란 선
    d.rectangle([0, horizon - 2, BG_W, horizon + 1], fill=hx("#1f6b3a"))
    d.line([(0, horizon - 3), (BG_W, horizon - 3)], fill=hx("#ffd84a"))
    # 잔디 줄무늬 (멀수록 촘촘)
    top = horizon + 2
    bands = 12
    for i in range(bands):
        y0 = top + (BG_H - top) * (i / bands) ** 2.0
        y1 = top + (BG_H - top) * ((i + 1) / bands) ** 2.0
        d.rectangle([0, y0, BG_W, y1], fill=hx("#4cc46a") if i % 2 == 0 else hx("#5fd27c"))
    dirt, dirt_s = hx("#e2a66b"), hx("#c98d55")
    # 내야 흙·잔디
    d.ellipse(_box((BG_W / 2, Y(MOUND_Y - 0.01)), 0.5 * SCREEN_W / P, Y(0.05)), fill=dirt)
    d.ellipse(_box((BG_W / 2, Y(MOUND_Y + 0.045)), 0.38 * SCREEN_W / P, Y(0.05)), fill=hx("#5fd27c"))
    # 파울 라인
    home = (BG_W / 2, Y(0.93))
    d.line([home, (X(0.04), Y(HORIZON_Y + 0.045))], fill=hx("#ffffff"))
    d.line([home, (X(0.96), Y(HORIZON_Y + 0.045))], fill=hx("#ffffff"))
    # 마운드
    d.ellipse(_box((BG_W / 2, Y(MOUND_Y)), 0.06 * SCREEN_W / P, 2.5), fill=dirt, outline=dirt_s)
    d.rectangle([BG_W / 2 - 2, Y(MOUND_Y) - 1, BG_W / 2 + 1, Y(MOUND_Y)], fill=hx("#ffffff"))
    # 홈 주변 흙(크게), 타석 박스(타자 발이 안에 들어가게 넓게, 원근으로 위가 좁다), 홈플레이트
    d.ellipse(_box((BG_W / 2, Y(1.02)), 0.42 * SCREEN_W / P, Y(0.2)), fill=dirt, outline=dirt_s)
    for s in (-1, 1):
        box = [(X(0.5 + s * 0.05), Y(0.84)), (X(0.5 + s * 0.22), Y(0.84)), (X(0.5 + s * 0.3), Y(1.04)), (X(0.5 + s * 0.06), Y(1.04))]
        d.line(box + [box[0]], fill=hx("#ffffff"))
    pw = 0.035 * SCREEN_W / P
    d.polygon([(BG_W / 2 - pw, Y(0.905)), (BG_W / 2 + pw, Y(0.905)), (BG_W / 2 + pw, Y(0.93)), (BG_W / 2, Y(0.955)), (BG_W / 2 - pw, Y(0.93))], fill=hx("#ffffff"), outline=hx("#b9bfd0"))
    return im


# ---------- 수비 화면(중계)의 선수 ----------
# 홈 뒤 높은 곳에서 내려다보는 화면이라 선수는 아주 작게, 얼굴 없이 모자·피부·유니폼·다리만 (5×8).
# 원근으로 크기를 바꾸지 않는다(도트 게임식). 팀마다 색만 다르다(T 모자·소매).
# 칸: 서기 · 달리기 둘 (팀마다 3칸, 우리 팀 먼저). 앵커 = 발 (2, 7)
FIELDER_CELL = (5, 8, 2, 7)
FIELDER_FRAMES = {
    "stand": [
        ".KKK.",
        "KTTTK",
        "KSSSK",
        "KTWTK",
        "KWWwK",
        ".KWK.",
        "KWKWK",
        "KK.KK",
    ],
    "run_a": [
        ".KKK.",
        "KTTTK",
        "KSSSK",
        "KTWTK",
        "KWWwK",
        "KWKWK",
        "KK.KK",
        ".....",
    ],
    "run_b": [
        ".KKK.",
        "KTTTK",
        "KSSSK",
        "KTWTK",
        "KWWwK",
        ".KWK.",
        ".KWK.",
        ".KKK.",
    ],
}
FIELDER_ORDER = ["stand", "run_a", "run_b"]


def make_fielders():
    w, h, ax, ay = FIELDER_CELL
    teams = [("us", "us_s"), ("them", "them_s")]
    im = Image.new("RGBA", (w * len(FIELDER_ORDER) * len(teams), h))
    px = im.load()
    for ti, (main, shade) in enumerate(teams):
        colors = {"K": "ink", "T": main, "t": shade, "S": "skin", "W": "jersey", "w": "jersey_s"}
        for fi, name in enumerate(FIELDER_ORDER):
            ox = (ti * len(FIELDER_ORDER) + fi) * w
            for y, row in enumerate(FIELDER_FRAMES[name]):
                for x, ch in enumerate(row):
                    if ch != ".":
                        px[ox + x, y] = (*PAL[colors[ch]], 255)
    return im


# ---------- 내보내기 ----------

def sheet(cells):
    w, h = cells[0].w, cells[0].h
    im = Image.new("RGBA", (w * len(cells), h))
    for i, c in enumerate(cells):
        im.paste(c.image(), (i * w, 0))
    return im


def gd_vec(p):
    return "Vector2(%g, %g)" % (round(p[0], 2), round(p[1], 2))


def main():
    os.makedirs(OUT, exist_ok=True)
    make_field().save(os.path.join(OUT, "field.png"))
    sheet([draw_batter(k) for _, k in BATTER_FRAMES]).save(os.path.join(OUT, "batter.png"))
    sheet([draw_pitcher(k) for _, k, _ in PITCHER_FRAMES]).save(os.path.join(OUT, "pitcher.png"))
    draw_catcher().image().save(os.path.join(OUT, "catcher.png"))
    draw_mitt().image().save(os.path.join(OUT, "mitt.png"))
    make_fielders().save(os.path.join(OUT, "fielders.png"))

    windup = [(t, i) for i, (_, _, t) in enumerate(PITCHER_FRAMES) if not isinstance(t, str)]
    after = [(float(t[1:]), i) for i, (_, _, t) in enumerate(PITCHER_FRAMES) if isinstance(t, str)]
    release = PITCHER_KEYS["release"]["t_hand"]
    lines = [
        "class_name PixelMeta",
        "extends RefCounted",
        "## scripts/make_pixel_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.",
        "## 좌표는 그림 픽셀(화면에서 SCALE 배), 발 자리 기준·위가 -.",
        "",
        "const SCALE := %d" % P,
        "const FIELD_SIZE := Vector2(%d, %d)" % (BG_W, BG_H),
        "const BATTER_CELL := Vector2(%d, %d)" % BATTER_CELL[:2],
        "const BATTER_FEET := Vector2(%d, %d)" % BATTER_CELL[2:],
        "const BATTER := {%s}" % ", ".join('"%s": %d' % (n, i) for i, (n, _) in enumerate(BATTER_FRAMES)),
        "const PITCHER_CELL := Vector2(%d, %d)" % PITCHER_CELL[:2],
        "const PITCHER_FEET := Vector2(%d, %d)" % PITCHER_CELL[2:],
        "## [와인드업 진행률, 칸] — 진행률이 넘으면 그 칸",
        "const PITCHER_WINDUP := [%s]" % ", ".join("[%g, %d]" % w for w in windup),
        "## [공을 놓은 뒤 ms, 칸]",
        "const PITCHER_AFTER := [%s]" % ", ".join("[%g, %d]" % a for a in after),
        "## 공이 출발하는 손 자리 (공 놓는 칸)",
        "const PITCHER_RELEASE := %s" % gd_vec(add(release, (0, -1))),
        "const CATCHER_CELL := Vector2(%d, %d)" % CATCHER_CELL[:2],
        "const CATCHER_FEET := Vector2(%d, %d)" % CATCHER_CELL[2:],
        "## 포수 그림에서 헬멧 꼭대기 (발 자리 기준)",
        "const CATCHER_TOP := -40.0",
        "const MITT_SIZE := Vector2(13, 13)",
        "## 수비 화면 선수: 칸 크기·발 자리, 칸 순서 (팀마다 서기·달리기 둘, 우리 팀 먼저)",
        "const FIELDER_CELL := Vector2(%d, %d)" % FIELDER_CELL[:2],
        "const FIELDER_FEET := Vector2(%d, %d)" % FIELDER_CELL[2:],
        "const FIELDER_FRAMES := %d" % len(FIELDER_ORDER),
        "",
    ]
    with open(META, "w") as f:
        f.write("\n".join(lines))
    # 확인용 확대본 (저장소에 넣지 않는다)
    preview = os.path.join(ROOT, "godot", "build", "shots")
    os.makedirs(preview, exist_ok=True)
    for name in ("batter", "pitcher", "catcher"):
        im = Image.open(os.path.join(OUT, name + ".png"))
        bg = Image.new("RGBA", im.size, hx("#5fd27c") + (255,))
        bg.alpha_composite(im)
        bg.resize((im.width * 4, im.height * 4), Image.NEAREST).save(os.path.join(preview, "pixel-%s.png" % name))
    print("done")


if __name__ == "__main__":
    main()
