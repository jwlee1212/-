#!/usr/bin/env python3
"""외부 그림 시트(SpriteCook 등 칠한 그림) → 우리 도트 규칙의 시트 (2026-11-07 시험).

순서: 칸 자르기 → 작은 덩어리(공 등) 지우기 → 큰 그림에서 픽셀마다 팔레트 재료(유니폼·남색·피부·배트·신발)를 고르고
재료 안의 밝기로 4단계(빛·바탕·그늘·깊은 그늘) 중 하나를 고른다 → 블록마다 가장 많은 색으로 줄인다(뭉개지지 않게)
→ 외톨이 점 정리 → 바깥 윤곽 1픽셀 남색(ink) → 모든 칸의 발 바닥을 같은 줄에 맞춘다.

    python3 scripts/pixelize_sheet.py 원본.webp 칸수 출력.png [--scale 0.45]

미리보기(원본 | 도트 3배)를 출력 이름 옆 *_preview.png 로 같이 쓴다. pillow·numpy 필요.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from make_pixel_art import PAL  # noqa: E402  (팔레트는 인게임 도트와 같은 것을 쓴다)

# 재료별 4단계 (밝은 쪽 → 어두운 쪽)
RAMPS = {
    "jersey": ["jersey_hi", "jersey", "jersey_s", "jersey_dk"],
    "us": ["us_hi", "us", "us_s", "us_dk"],      # 원본의 남색 헬멧·소매·양말·줄무늬 → 우리 팀 파랑
    "skin": ["skin_hi", "skin", "skin_s", "skin_dk"],
    "bat": ["bat_hi", "bat", "bat_s", "bat_dk"],
    "shoe": ["shoe_hi", "shoe", "shoe_s", "shoe_dk"],
}
KEYS = ["ink"] + [k for r in RAMPS.values() for k in r]
KEY_ID = {k: i for i, k in enumerate(KEYS)}
COLORS = np.array([PAL[k] for k in KEYS], dtype=np.uint8)


def classify(rgb):
    """큰 그림 픽셀(N,3 float 0~1) → 팔레트 키 번호. 재료는 색상·채도로, 단계는 재료 안 밝기로."""
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    mx, mn = rgb.max(1), rgb.min(1)
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    warm = (r - b)
    out = np.empty(len(rgb), dtype=np.int32)

    def ramp(mask, name, cuts):
        # cuts: 밝기 경계 3개 (밝은 단계부터)
        idx = np.digitize(-lum[mask], [-c for c in cuts])
        out[mask] = np.array([KEY_ID[k] for k in RAMPS[name]])[idx]

    ink = lum < 0.07
    navy = ~ink & (b > r + 0.04) & (lum < 0.40)
    warmish = ~ink & ~navy & (warm > 0.12) & (sat > 0.22)
    # 배트는 노랑 쪽(초록이 빨강에 가까움), 피부는 붉은 쪽
    bat = warmish & ((g / np.maximum(r, 1e-6)) > 0.70)
    skin = warmish & ~bat
    dark = ~ink & ~navy & ~warmish & (lum < 0.30)
    jersey = ~ink & ~navy & ~warmish & ~dark
    out[ink] = KEY_ID["ink"]
    ramp(navy, "us", [0.30, 0.20, 0.12])
    ramp(bat, "bat", [0.70, 0.55, 0.38])
    ramp(skin, "skin", [0.70, 0.55, 0.40])
    ramp(dark, "shoe", [0.26, 0.18, 0.11])
    # 유니폼이 너무 하얗게 날아가지 않게 빛 단계는 가장 밝은 쪽만
    ramp(jersey, "jersey", [0.86, 0.74, 0.56])
    return out


def components(mask):
    """4방향 연결 덩어리 번호판"""
    h, w = mask.shape
    lab = np.zeros((h, w), np.int32)
    n = 0
    for y0, x0 in zip(*np.nonzero(mask)):
        if lab[y0, x0]:
            continue
        n += 1
        st = [(y0, x0)]
        lab[y0, x0] = n
        while st:
            y, x = st.pop()
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n
                    st.append((yy, xx))
    return lab, n


def keep_main(alpha):
    """가장 큰 덩어리와, 그 크기의 3% 넘는 덩어리만 남긴다 (공·먼지 지우기)"""
    lab, n = components(alpha)
    if n <= 1:
        return alpha
    sizes = np.bincount(lab.ravel())[1:]
    keep = [i + 1 for i, s in enumerate(sizes) if s >= sizes.max() * 0.03]
    return np.isin(lab, keep)


def downscale(keys, alpha, s):
    """블록마다 가장 많은 키 (투명이 절반 넘으면 투명) — 섞인 색이 생기지 않는다"""
    h, w = keys.shape
    oh, ow = int(np.ceil(h * s)), int(np.ceil(w * s))
    out = np.full((oh, ow), -1, np.int32)
    ys = (np.arange(oh + 1) / s).astype(int)
    xs = (np.arange(ow + 1) / s).astype(int)
    for oy in range(oh):
        for ox in range(ow):
            blk_a = alpha[ys[oy]:max(ys[oy + 1], ys[oy] + 1), xs[ox]:max(xs[ox + 1], xs[ox] + 1)]
            if blk_a.size == 0 or blk_a.mean() < 0.5:
                continue
            blk = keys[ys[oy]:max(ys[oy + 1], ys[oy] + 1), xs[ox]:max(xs[ox + 1], xs[ox] + 1)][blk_a]
            # 윤곽선(ink)은 다시 두를 것이라 블록 안 다른 색이 있으면 그쪽을 고른다
            nz = blk[blk != KEY_ID["ink"]]
            out[oy, ox] = np.bincount(nz if nz.size else blk).argmax()
    return out


def cleanup(k):
    """외톨이 점: 상하좌우 넷이 모두 나와 다르고 그중 셋 이상이 같은 색이면 그 색으로"""
    h, w = k.shape
    r = k.copy()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            c = k[y, x]
            if c < 0:
                continue
            nb = [k[y - 1, x], k[y + 1, x], k[y, x - 1], k[y, x + 1]]
            if c not in nb:
                vals, cnt = np.unique(nb, return_counts=True)
                if cnt.max() >= 3 and vals[cnt.argmax()] >= 0:
                    r[y, x] = vals[cnt.argmax()]
    return r


MATERIAL = {KEY_ID[k]: name for name, r in RAMPS.items() for k in r}


def smooth(k):
    """같은 재료 안의 단계 얼룩 정리: 3×3 이웃 중 같은 단계가 하나뿐이면 이웃에서 가장 많은 같은 재료 단계로"""
    h, w = k.shape
    r = k.copy()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            c = k[y, x]
            if c < 0 or c not in MATERIAL:
                continue
            nb = k[y - 1:y + 2, x - 1:x + 2].ravel()
            nb = np.delete(nb, 4)
            same_mat = [v for v in nb if v in MATERIAL and MATERIAL[v] == MATERIAL[c]]
            if (nb == c).sum() <= 1 and len(same_mat) >= 5:
                vals, cnt = np.unique(same_mat, return_counts=True)
                r[y, x] = vals[cnt.argmax()]
    return r


def redraw_bat(k):
    """배트를 반듯하게 다시 그린다: 배트 재료 칸으로 직선을 맞추고, 손에서 먼 쪽(배럴)은 2칸·손잡이는 1칸 굵기.
    위 가장자리 빛·아래 가장자리 그늘 (빛은 왼쪽 위). 손과 겹친 칸은 지우지 않는다."""
    bat_ids = [KEY_ID[x] for x in RAMPS["bat"]]
    is_bat = np.isin(k, bat_ids)
    lab, n = components(is_bat)
    if n == 0:
        return k
    big = np.bincount(lab.ravel())[1:].argmax() + 1
    ys, xs = np.nonzero(lab == big)
    if len(ys) < 8:
        return k
    pts = np.stack([xs, ys], 1).astype(float)
    ctr = pts.mean(0)
    _, _, vt = np.linalg.svd(pts - ctr)
    d = vt[0]
    t = (pts - ctr) @ d
    a, b = ctr + d * t.min(), ctr + d * t.max()
    body = (k >= 0) & ~is_bat
    by, bx = np.nonzero(body)
    bc = np.array([bx.mean(), by.mean()])
    # 몸 가운데에서 먼 끝이 배럴
    barrel, knob = (a, b) if np.linalg.norm(a - bc) > np.linalg.norm(b - bc) else (b, a)
    # 몸에서 3칸 넘게 떨어진 옛 배트 칸만 지운다 (손 아래 칸은 남김)
    near_body = body.copy()
    for _ in range(2):
        near_body = near_body | np.pad(near_body, 1)[:-2, 1:-1] | np.pad(near_body, 1)[2:, 1:-1] \
            | np.pad(near_body, 1)[1:-1, :-2] | np.pad(near_body, 1)[1:-1, 2:]
    r = k.copy()
    r[(lab == big) & ~near_body] = -1
    # 새 배트: 선을 촘촘히 따라가며 칸을 찍는다
    L = np.linalg.norm(barrel - knob)
    steps = int(L * 3) + 1
    perp = np.array([-d[1], d[0]])
    if perp[1] > 0:
        perp = -perp  # 위쪽을 향하게
    for i in range(steps + 1):
        f = i / steps  # 0 = 손잡이, 1 = 배럴
        p = knob + (barrel - knob) * f
        x, y = int(round(p[0])), int(round(p[1]))
        if 0 <= y < k.shape[0] and 0 <= x < k.shape[1] and not body[y, x]:
            r[y, x] = KEY_ID["bat_s" if f > 0.45 else "bat"]
        if f > 0.45:
            q = p + perp * 0.9
            x2, y2 = int(round(q[0])), int(round(q[1]))
            if 0 <= y2 < k.shape[0] and 0 <= x2 < k.shape[1] and not body[y2, x2]:
                r[y2, x2] = KEY_ID["bat_hi"] if f > 0.6 else KEY_ID["bat"]
    return r


def outline(k):
    """바깥 윤곽 1픽셀 ink 를 그림 바깥에 두른다 (make_pixel_art 와 같게 — 안쪽을 덮으면 배트처럼 얇은 건 다 먹힌다)"""
    solid = k >= 0
    pad = np.pad(solid, 1)
    ring = (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]) & ~solid
    r = k.copy()
    r[ring] = KEY_ID["ink"]
    return r


# --batter: 원본 10칸 → 게임 타자 시트의 이름 칸(make_pixel_art.BATTER_FRAMES 순서). 값 = 원본 칸 번호(0부터).
# 원본: 0 준비 · 1 숨 · 2 로드 · 3 다리 들기 · 4 내딛기 · 5 컨택 · 6 팔 뻗기 · 7·8 팔로스루 · 9 마무리. 번트 칸은 원본에 없어
# 임시로 내딛기 칸을 쓴다.
BATTER_MAP = {
    "stance": 0, "breath": 1, "load_a": 1, "load": 2, "stride_up": 3, "stride": 3, "landing": 4,
    "rotate1": 4, "rotate2": 5, "contact_high": 5, "contact_mid": 5, "contact_low": 5, "extend": 6,
    "follow1": 6, "follow2": 7, "follow3": 8, "finish": 9, "bunt": 4, "recover": 9, "settle": 4,
}
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def write_batter(sheet, w, h, foot, frames):
    """게임 타자 시트 godot/art/pixel/batter_ext.png + 칸 정보 BatterExtMeta (BatterSprite 가 이 파일이 있으면 쓴다)"""
    from make_pixel_art import BATTER_FRAMES
    names = [n for n, _ in BATTER_FRAMES]
    out = np.zeros((h, w * len(names), 4), np.uint8)
    for j, n in enumerate(names):
        i = BATTER_MAP[n]
        assert i < frames, n
        out[:, j * w:(j + 1) * w] = sheet[:, i * w:(i + 1) * w]
    Image.fromarray(out).save(os.path.join(ROOT, "godot", "art", "pixel", "batter_ext.png"))
    # 발 자리: 준비 칸 바닥 6줄에서 칠한 칸들의 가운데 (두 발 사이)
    st = sheet[:, BATTER_MAP["stance"] * w:(BATTER_MAP["stance"] + 1) * w, 3] > 0
    xs = np.nonzero(st[foot - 5:foot + 1].any(0))[0]
    fx = int(round((xs.min() + xs.max()) / 2))
    meta = (
        "class_name BatterExtMeta\n"
        "## scripts/pixelize_sheet.py --batter 가 만든다. 손으로 고치지 않는다.\n"
        "## 외부 그림(SpriteCook)을 도트로 바꾼 타자 시트 art/pixel/batter_ext.png 의 칸 정보.\n"
        "## 칸 순서는 PixelMeta.BATTER 와 같다.\n"
        "const CELL := Vector2(%d, %d)\n"
        "const FEET := Vector2(%d, %d)\n" % (w, h, fx, foot + 1)
    )
    with open(os.path.join(ROOT, "godot", "scripts", "characters", "batter_ext_meta.gd"), "w") as f:
        f.write(meta)
    print("batter_ext", len(names), "cells", w, h, "feet", fx, foot + 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("frames", type=int)
    ap.add_argument("out")
    ap.add_argument("--scale", type=float, default=0.45)
    ap.add_argument("--batter", action="store_true", help="게임 타자 시트(batter_ext)로도 내보낸다")
    a = ap.parse_args()

    im = np.array(Image.open(a.src).convert("RGBA")).astype(np.float32) / 255
    H, W = im.shape[:2]
    cw = W // a.frames
    cells = []
    for i in range(a.frames):
        c = im[:, i * cw:(i + 1) * cw]
        alpha = keep_main(c[..., 3] > 0.5)
        keys = np.full(alpha.shape, -1, np.int32)
        keys[alpha] = classify(c[..., :3][alpha])
        k = downscale(keys, alpha, a.scale)
        k = outline(redraw_bat(smooth(cleanup(k))))
        cells.append(k)

    # 발 바닥 줄 맞추기: 칸마다 가장 아래 칠한 줄을 같은 높이로
    h = max(c.shape[0] for c in cells)
    w = max(c.shape[1] for c in cells)
    bottoms = [np.nonzero((c >= 0).any(1))[0].max() for c in cells]
    foot = max(bottoms)
    sheet = np.zeros((h, w * a.frames, 4), np.uint8)
    for i, c in enumerate(cells):
        dy = foot - bottoms[i]
        for y, x in zip(*np.nonzero(c >= 0)):
            if y + dy < h:
                sheet[y + dy, i * w + x] = (*COLORS[c[y, x]], 255)
    Image.fromarray(sheet).save(a.out)
    if a.batter:
        write_batter(sheet, w, h, foot, a.frames)

    # 미리보기: 위 원본(축소), 아래 도트 3배
    z = 3
    dot = Image.fromarray(sheet).resize((sheet.shape[1] * z, h * z), Image.NEAREST)
    src = Image.open(a.src).convert("RGBA")
    src = src.resize((dot.width, int(src.height * dot.width / src.width)), Image.LANCZOS)
    pv = Image.new("RGBA", (dot.width, src.height + dot.height + 8), (120, 170, 110, 255))
    pv.alpha_composite(src, (0, 0))
    pv.alpha_composite(dot, (0, src.height + 8))
    pv.save(os.path.splitext(a.out)[0] + "_preview.png")
    print("cell", w, h, "foot", foot)


if __name__ == "__main__":
    main()
