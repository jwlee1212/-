#!/usr/bin/env python3
"""훈련 장면 도트 (2026-11-01): 주간 스케줄을 실행할 때 화면 가운데 창에 뜨는 훈련 애니메이션을 코드로 찍어 만든다.
AI 생성 그림을 쓰지 않는다. 테두리·그늘 규칙과 색은 make_pixel_art.py 와 같다 (그 파일의 도구를 가져다 쓴다).

장면마다 FRAMES 장, 왼쪽부터 차례로 되풀이한다 (줄 = 장면):
    contact  타격 훈련   실내 연습장 그물 앞, 티 배팅 (준비 → 내딛기 → 맞는 순간 → 팔로스루)
    power    웨이트      헬스장, 바벨 스쿼트 (서기 → 반 → 앉기 → 반)
    eye      선구안 훈련 피칭머신 공을 끝까지 보고 참는다 (공이 오른쪽에서 날아와 뒤 그물에 꽂힌다)
    speed    주루 훈련   운동장 트랙 달리기 (뛰기 4장, 뒤 콘이 흘러간다)
    rest     휴식        내 방 침대에 누워 잔다 (이불이 숨 따라 오르내리고 Z 가 떠오른다)
    fielding 수비 훈련   내야 흙 위 펑고 땅볼: 자세 잡기 → 앞으로 대시 → 낮게 잡기(흙먼지) → 송구 (2026-11-01)
주인공은 얼굴을 그리지 않는다 (유저 몰입, 2026-11-01) — 모자 챙 그늘이 눈 자리를 덮는다. 누워 있을 때는 뒤통수만.

    python3 scripts/make_training_art.py   (pillow·numpy 필요)

나오는 것: godot/art/pixel/training.png, godot/scripts/characters/training_meta.gd
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from make_pixel_art import PAL, Cell, capsule, circle, ellipse, poly, along, lerp, hx, OUT  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
META = os.path.join(ROOT, "godot", "scripts", "characters", "training_meta.gd")

W, H = 120, 72
FRAMES = 4
SCENES = ["contact", "power", "eye", "speed", "rest", "fielding"]
FLOOR = 66  # 발 자리 y

PAL.update({k: hx(v) for k, v in {
    # 연습복: 팀 파랑 반팔 + 회색 바지 + 파랑 모자
    "shirt": "#2f62e0", "shirt_s": "#1d3d9e", "pants": "#8a91a8", "pants_s": "#626a82",
    "cap": "#1d3d9e", "cap_s": "#152c70",
    "iron": "#3a3f55", "iron_s": "#24283a", "iron_hi": "#6b7290",
    # 배경
    "net": "#2c5a46", "net_line": "#3f7a5f", "turf": "#4a9a5c", "turf_s": "#3b8049",
    "gym_wall": "#d9cdb8", "gym_wall_s": "#c4b69c", "gym_floor": "#5a4a40", "mat": "#2e3348", "mirror": "#bcd3e0",
    "sky": "#9fd0f0", "track": "#c9604a", "track_s": "#a94d3a", "line": "#f4efe6", "grass": "#5cb86c",
    "cone": "#ff7a2a", "cone_s": "#c95510",
    "room_wall": "#3a3f66", "room_wall_s": "#30345a", "night": "#1b2040", "moon": "#fff3b0",
    "bed": "#a0724a", "bed_s": "#7a5434", "sheet": "#f4efe6", "blanket": "#e98a6a", "blanket_s": "#c96a4c", "pillow": "#ffffff",
    "machine": "#9aa3b8", "machine_s": "#6b7290", "tee": "#2a2b38",
    "dirt": "#e2a66b", "dirt_s": "#c98d55", "fence": "#1f6b3a", "fence_top": "#ffd84a",
}.items()})


def rect(c, x0, y0, x1, y1, color):
    c.rgba[max(0, y0):min(H, y1), max(0, x0):min(W, x1)] = (*PAL[color], 255)


def stamp(c, rows, x, y, colors):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in colors and 0 <= y + j < H and 0 <= x + i < W:
                c.rgba[y + j, x + i] = (*PAL[colors[ch]], 255)


# ---------- 주인공 (옆모습, 오른쪽을 본다) ----------
# 포즈: 머리 head, 어깨 sh, 엉덩이 hip, 팔(e: 팔꿈치, h: 손) 앞 f·뒤 b, 다리(k: 무릎, ft: 발) 앞 f·뒤 b

def guy(c, k, cap=True):
    def arm(sh, e, h):
        c.part(capsule(sh, e, 4), "shirt", "shirt_s")
        c.part(capsule(e, h, 3), "skin", "skin_s")

    def leg(hip, kn, ft):
        c.part(capsule(hip, kn, 5), "pants", "pants_s")
        c.part(capsule(kn, ft, 4), "pants", "pants_s")
        c.part(ellipse((ft[0] + 1.5, ft[1]), 3, 1.5), "shoe", "shoe_s")

    arm(k["sh"], k["be"], k["bh"])
    leg(k["hip"], k["bk"], k["bft"])
    c.part(capsule(k["sh"], k["hip"], 9), "shirt", "shirt_s")
    leg(k["hip"], k["fk"], k["fft"])
    hx_, hy = k["head"]
    c.part(circle((hx_, hy), 6), "skin", "skin_s")
    if cap:
        # 모자: 머리 위 반원 + 앞(오른쪽)으로 나온 챙. 챙 아래 눈 자리는 그늘로 덮는다 (얼굴 없음)
        c.part(ellipse((hx_ - 0.5, hy - 2.5), 6.5, 4.5), "cap", "cap_s")
        c.part(poly([(hx_ + 2, hy - 2), (hx_ + 10, hy - 1), (hx_ + 10, hy + 0.5), (hx_ + 2, hy + 0.5)]), "cap_s")
        c.put(c._mask(poly([(hx_ + 1, hy + 1), (hx_ + 6, hy + 1), (hx_ + 6, hy + 2), (hx_ + 1, hy + 2)])), PAL["skin_s"])
    if "bat" in k:
        h, ang = k["bat"]
        c.part(capsule(h, along(h, ang, 22), 3), "bat", "bat_s")
    arm(k["sh"], k["fe"], k["fh"])


def standing(x, **over):
    """서 있는 기본 포즈 (발 자리 x). over 로 관절을 바꾼다"""
    k = dict(head=(x + 1, 25), sh=(x, 34), hip=(x - 1, 49),
             be=(x - 2, 41), bh=(x, 47), fe=(x + 1, 41), fh=(x + 3, 47),
             bk=(x - 3, 57), bft=(x - 4, FLOOR - 1), fk=(x + 2, 57), fft=(x + 3, FLOOR - 1))
    k.update(over)
    return k


# ---------- 장면 ----------

def bg_cage(c):
    rect(c, 0, 0, W, FLOOR - 4, "net")
    for x in range(0, W, 6):
        rect(c, x, 0, x + 1, FLOOR - 4, "net_line")
    for y in range(2, FLOOR - 4, 6):
        rect(c, 0, y, W, y + 1, "net_line")
    rect(c, 0, FLOOR - 4, W, H, "turf")
    rect(c, 0, FLOOR - 4, W, FLOOR - 3, "turf_s")


def scene_contact(i):
    c = Cell(W, H, 0, 0)
    bg_cage(c)
    x = 46
    tee_x, ball_y = 74, 47
    # 티: 받침 + 기둥
    c.part(poly([(tee_x - 5, FLOOR), (tee_x + 5, FLOOR), (tee_x + 3, FLOOR - 3), (tee_x - 3, FLOOR - 3)]), "tee")
    c.part(capsule((tee_x, FLOOR - 3), (tee_x, ball_y + 3), 2), "tee")
    poses = [
        standing(x, be=(x - 6, 36), bh=(x - 4, 30), fe=(x - 2, 38), fh=(x - 4, 30), bat=((x - 4, 30), -115)),
        standing(x, fk=(x + 5, 57), fft=(x + 8, FLOOR - 1), be=(x - 7, 37), bh=(x - 5, 31), fe=(x - 3, 39), fh=(x - 5, 31), bat=((x - 5, 31), -140)),
        standing(x + 1, head=(x + 3, 26), sh=(x + 1, 35), fk=(x + 6, 57), fft=(x + 8, FLOOR - 1), be=(x + 1, 42), bh=(x + 7, 45), fe=(x + 5, 40), fh=(x + 7, 45), bat=((x + 7, 45), 2)),
        standing(x + 1, head=(x + 3, 26), sh=(x + 1, 35), fk=(x + 6, 57), fft=(x + 8, FLOOR - 1), bk=(x - 1, 58), bft=(x - 3, FLOOR - 2),
                 be=(x + 5, 37), bh=(x + 3, 30), fe=(x + 6, 35), fh=(x + 3, 30), bat=((x + 3, 30), -165)),
    ]
    k = poses[i]
    if i < 2:
        c.part(circle((tee_x, ball_y), 2), "ball", "ball_s")
    elif i == 2:
        c.part(circle((tee_x, ball_y - 1), 2), "ball", "ball_s")
        for d in [(-3, -4), (4, -5), (5, 2), (-2, 4)]:  # 맞는 순간 파편
            c.put(c._mask(circle((tee_x + d[0] * 1.5, ball_y + d[1]), 0.6)), PAL["line"])
    else:
        c.part(circle((108, 18), 2), "ball", "ball_s")
        for t in range(1, 4):
            p = lerp((108, 18), (tee_x, ball_y), t * 0.18)
            c.put(c._mask(circle(p, 0.6)), PAL["line"], 160 - t * 40)
    guy(c, k)
    return c


def scene_power(i):
    c = Cell(W, H, 0, 0)
    rect(c, 0, 0, W, FLOOR - 4, "gym_wall")
    for y in range(8, FLOOR - 4, 10):
        rect(c, 0, y, W, y + 1, "gym_wall_s")
    rect(c, 8, 10, 34, 46, "mirror")
    rect(c, 8, 10, 34, 11, "iron_s")
    # 덤벨 거치대 (오른쪽)
    rect(c, 84, 40, 112, 42, "iron_s")
    rect(c, 86, 42, 88, FLOOR - 4, "iron_s")
    rect(c, 108, 42, 110, FLOOR - 4, "iron_s")
    for j, dx in enumerate(range(88, 108, 7)):
        c.part(capsule((dx, 37), (dx + 4, 37), 4 - (j % 2)), "iron", "iron_s")
    rect(c, 0, FLOOR - 4, W, H, "gym_floor")
    rect(c, 36, FLOOR - 3, 76, FLOOR, "mat")
    x = 56
    depth = [0.0, 0.5, 1.0, 0.5][i]

    def at(a, b):
        return lerp(a, b, depth)
    head = at((x + 1, 25), (x + 4, 34))
    sh = at((x, 34), (x + 2, 43))
    hip = at((x - 1, 49), (x - 7, 56))
    knee = at((x + 2, 57), (x + 7, 57))
    k = standing(x, head=head, sh=sh, hip=hip, bk=knee, fk=(knee[0] + 1, knee[1]),
                 bft=(x - 1, FLOOR - 1), fft=(x + 1, FLOOR - 1),
                 be=(sh[0] - 5, sh[1] + 2), bh=(sh[0] - 3, sh[1] - 2), fe=(sh[0] - 4, sh[1] + 3), fh=(sh[0] - 2, sh[1] - 2))
    # 바벨 (끝에서 본 원판): 어깨 위 목 뒤에 얹혀 있다. 먼저 그려 몸·손이 앞에 오게 한다
    plate = (sh[0] - 7, sh[1] - 3)
    c.part(circle(plate, 7), "iron", "iron_s")
    c.part(circle(plate, 2), "iron_hi")
    guy(c, k)
    if i == 2:  # 앉은 순간 땀방울
        c.part(ellipse((head[0] + 8, head[1] - 6), 1, 1.5), "mirror")
    return c


def scene_eye(i):
    c = Cell(W, H, 0, 0)
    bg_cage(c)
    # 피칭머신 (오른쪽): 다리 셋 + 몸통 + 바퀴 둘 + 공 바구니
    mx = 100
    c.part(capsule((mx - 4, FLOOR), (mx, 46), 2), "machine_s")
    c.part(capsule((mx + 5, FLOOR), (mx, 46), 2), "machine_s")
    c.part(poly([(mx - 7, 34), (mx + 8, 34), (mx + 8, 48), (mx - 7, 48)]), "machine", "machine_s")
    c.part(circle((mx - 4, 41), 4), "iron", "iron_s")
    c.part(circle((mx + 4, 41), 4), "iron", "iron_s")
    c.part(poly([(mx - 2, 26), (mx + 8, 26), (mx + 7, 33), (mx - 1, 33)]), "iron_s")
    for dx in (0, 3, 6):
        c.put(c._mask(circle((mx + dx, 25), 1.5)), PAL["ball"])
    x = 40
    # 공: 머신에서 나와 타자 앞을 지나 뒤 그물로. 타자는 휘두르지 않고 고개만 공을 따라간다
    ball_x = [86, 66, 46, 14][i]
    ball_y = [40, 43, 46, 50][i]
    turn = [0, 0, 1, 2][i]
    k = standing(x, head=(x + 1 - turn, 25), be=(x - 6, 36), bh=(x - 4, 30), fe=(x - 2, 38), fh=(x - 4, 30), bat=((x - 4, 30), -115))
    if i == 3:
        guy(c, k)
        c.part(circle((ball_x, ball_y), 2), "ball", "ball_s")
        for d in [(-2, -3), (2, -3), (0, -4)]:  # 그물 흔들림
            c.put(c._mask(circle((ball_x + d[0], ball_y + d[1]), 0.6)), PAL["line"])
    else:
        guy(c, k)
        c.part(circle((ball_x, ball_y), 2), "ball", "ball_s")
        for t in range(1, 3):
            c.put(c._mask(circle((ball_x + t * 4, ball_y - t * 0.6), 0.7)), PAL["line"], 170 - t * 60)
    return c


def scene_speed(i):
    c = Cell(W, H, 0, 0)
    rect(c, 0, 0, W, 30, "sky")
    rect(c, 0, 30, W, 46, "grass")
    rect(c, 0, 46, W, H, "track")
    rect(c, 0, 46, W, 47, "line")
    rect(c, 0, FLOOR + 3, W, FLOOR + 4, "line")
    # 콘: 장마다 8칸씩 왼쪽으로 (32칸 주기라 4장이 이어진다)
    for base in range(-32, W + 32, 32):
        cx = base - i * 8
        c.part(poly([(cx - 3, 54), (cx + 3, 54), (cx, 46)]), "cone", "cone_s")
    x = 56
    lean = (x + 4, 26)
    run = [
        dict(fk=(x + 7, 54), fft=(x + 12, 60), bk=(x - 4, 59), bft=(x - 10, 60), fe=(x - 3, 40), fh=(x - 6, 36), be=(x + 5, 40), bh=(x + 9, 36)),
        dict(fk=(x + 4, 57), fft=(x + 3, FLOOR - 1), bk=(x - 2, 54), bft=(x - 8, 55), fe=(x - 2, 41), fh=(x - 1, 46), be=(x + 3, 41), bh=(x + 5, 46)),
        dict(fk=(x - 4, 59), fft=(x - 10, 60), bk=(x + 7, 54), bft=(x + 12, 60), fe=(x + 5, 40), fh=(x + 9, 36), be=(x - 3, 40), bh=(x - 6, 36)),
        dict(fk=(x - 2, 54), fft=(x - 8, 55), bk=(x + 4, 57), bft=(x + 3, FLOOR - 1), fe=(x + 3, 41), fh=(x + 5, 46), be=(x - 2, 41), bh=(x - 1, 46)),
    ][i]
    bob = [0, 1, 0, 1][i]
    k = standing(x, head=(lean[0], lean[1] + bob), sh=(x + 2, 34 + bob), hip=(x, 48 + bob), **run)
    # 바람 줄
    for j, y in enumerate((30, 40, 50)):
        rect(c, x - 22 + (i * 3 + j * 5) % 8, y, x - 14 + (i * 3 + j * 5) % 8, y + 1, "line")
    guy(c, k)
    return c


Z = ["####", "..#.", ".#..", "####"]


def scene_rest(i):
    c = Cell(W, H, 0, 0)
    rect(c, 0, 0, W, H, "room_wall")
    for x in range(0, W, 12):
        rect(c, x, 0, x + 1, 48, "room_wall_s")
    # 창문: 밤하늘 + 달
    rect(c, 76, 8, 108, 32, "bed_s")
    rect(c, 78, 10, 106, 30, "night")
    c.part(circle((98, 17), 4), "moon")
    rect(c, 91, 10, 92, 30, "bed_s")
    rect(c, 78, 19, 106, 20, "bed_s")
    rect(c, 0, 58, W, H, "gym_floor")
    # 침대: 머리판 + 매트리스 + 다리
    c.part(poly([(12, 36), (18, 36), (18, 62), (12, 62)]), "bed", "bed_s")
    c.part(poly([(16, 48), (96, 48), (96, 56), (16, 56)]), "sheet", "jersey_s")
    c.part(poly([(16, 56), (96, 56), (96, 60), (16, 60)]), "bed", "bed_s")
    for lx in (18, 92):
        rect(c, lx, 60, lx + 3, 64, "bed_s")
    c.part(ellipse((28, 45), 8, 3.5), "pillow", "jersey_s")
    # 누운 주인공: 베개 위 뒤통수(머리카락) + 이불 아래 몸. 숨 쉴 때 이불이 한 칸 오르내린다
    c.part(circle((30, 41), 6), "hair")
    c.part(ellipse((33, 44), 3, 2), "skin", "skin_s")
    breathe = [0, 1, 1, 0][i]
    top = 41 - breathe
    c.part(poly([(34, 44), (44, top), (70, top + 1), (90, 43), (94, 48), (34, 48)]), "blanket", "blanket_s")
    rect(c, 40, top + 3, 88, top + 4, "blanket_s")
    # Z 가 떠오른다 (장마다 한 칸씩 위로, 셋이 번갈아)
    for j in range(3):
        t = (i + j * 2) % 6
        if t < 4:
            stamp(c, Z if j != 1 else [r[:3] for r in Z[:3]], 40 + j * 7 + t, 30 - t * 4 - j * 2, {"#": "line"})
    return c


def scene_fielding(i):
    c = Cell(W, H, 0, 0)
    # 하늘 → 외야 담장(노란 꼭대기) → 잔디(줄무늬) → 내야 흙(얼룩)
    rect(c, 0, 0, W, 18, "sky")
    rect(c, 0, 18, W, 19, "fence_top")
    rect(c, 0, 19, W, 26, "fence")
    for x in range(0, W, 16):
        rect(c, x, 26, x + 8, 46, "grass")
        rect(c, x + 8, 26, x + 16, 46, "turf")
    rect(c, 0, 46, W, H, "dirt")
    rect(c, 0, 46, W, 47, "dirt_s")
    for j in range(40):
        x = (j * 37) % W
        y = 49 + (j * 13) % (H - 50)
        rect(c, x, y, x + 1, y + 1, "dirt_s")
    x = 46
    ground = FLOOR - 2
    # 자세: 낮게 앉아 글러브(앞손)를 땅 가까이 — 대시 — 더 낮게 잡기 — 일어서며 송구 (공은 오른쪽 코치 쪽에서 굴러온다)
    crouch = dict(head=(x + 5, 33), sh=(x + 3, 41), hip=(x - 3, 51), bk=(x - 6, 58), bft=(x - 8, FLOOR - 1),
                  fk=(x + 5, 56), fft=(x + 8, FLOOR - 1), be=(x + 1, 48), bh=(x + 6, 55), fe=(x + 7, 48), fh=(x + 10, 56))
    poses = [
        standing(x, **crouch),
        standing(x + 5, head=(x + 11, 34), sh=(x + 9, 42), hip=(x + 2, 51), bk=(x - 2, 57), bft=(x - 5, FLOOR - 3),
                 fk=(x + 11, 56), fft=(x + 14, FLOOR - 1), be=(x + 6, 48), bh=(x + 11, 56), fe=(x + 13, 49), fh=(x + 16, 59)),
        standing(x + 6, head=(x + 13, 37), sh=(x + 11, 44), hip=(x + 3, 52), bk=(x - 1, 59), bft=(x - 3, FLOOR - 1),
                 fk=(x + 13, 57), fft=(x + 16, FLOOR - 1), be=(x + 9, 52), bh=(x + 16, 60), fe=(x + 14, 53), fh=(x + 18, 62)),
        standing(x + 6, head=(x + 13, 25), sh=(x + 11, 34), hip=(x + 9, 49), bk=(x + 5, 58), bft=(x + 2, FLOOR - 2),
                 fk=(x + 16, 57), fft=(x + 19, FLOOR - 1), be=(x + 5, 30), bh=(x + 2, 23), fe=(x + 17, 36), fh=(x + 20, 40)),
    ]
    k = poses[i]
    # 굴러오는 공 (꼬리 점) — 잡기 전 두 장
    if i < 2:
        bx = [108, 84][i]
        for t in range(1, 4):
            c.put(c._mask(circle((bx + t * 4, ground), 0.7)), PAL["line"], 180 - t * 50)
        c.part(circle((bx, ground), 2), "ball", "ball_s")
    guy(c, k)
    # 글러브 (앞손). 잡는 장은 글러브 안에 공, 발밑에 흙먼지
    gx, gy = k["fh"]
    c.part(ellipse((gx + 1, gy), 3.5, 2.5), "glove", "glove_s")
    if i == 2:
        c.part(circle((gx + 2, gy - 1), 1.5), "ball", "ball_s")
        for d in [(-6, 1), (4, 2), (8, 0), (-2, 3)]:
            c.part(circle((gx + d[0], FLOOR - 1 + d[1] * 0.3), 1.6 - abs(d[0]) * 0.08), "dirt_s", outline=False)
    if i == 3:
        # 던지는 손의 공 + 바람 줄
        c.part(circle(k["bh"], 1.6), "ball", "ball_s")
        for j, y in enumerate((22, 27)):
            rect(c, k["bh"][0] - 9 + j * 2, y, k["bh"][0] - 4 + j * 2, y + 1, "line")
    return c


DRAW = {"contact": scene_contact, "power": scene_power, "eye": scene_eye, "speed": scene_speed, "rest": scene_rest,
        "fielding": scene_fielding}


def main():
    os.makedirs(OUT, exist_ok=True)
    im = Image.new("RGBA", (W * FRAMES, H * len(SCENES)))
    for row, name in enumerate(SCENES):
        for i in range(FRAMES):
            im.paste(DRAW[name](i).image(), (i * W, row * H))
    im.save(os.path.join(OUT, "training.png"))
    lines = [
        "class_name TrainingMeta",
        "extends RefCounted",
        "## scripts/make_training_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.",
        "## art/pixel/training.png: 줄 = 장면 (훈련 id), 칸 = 장 (왼쪽부터 되풀이)",
        "",
        "const SIZE := Vector2(%d, %d)" % (W, H),
        "const FRAMES := %d" % FRAMES,
        "const ROWS := {%s}" % ", ".join('"%s": %d' % (n, i) for i, n in enumerate(SCENES)),
        "",
    ]
    with open(META, "w") as f:
        f.write("\n".join(lines))
    print("training.png %d×%d, 장면 %d개" % (im.width, im.height, len(SCENES)))


if __name__ == "__main__":
    main()
