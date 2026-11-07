#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SpriteCook 애니메이션 시트 정리 (2026-11-07 — 인게임 그림은 SpriteCook 원본 그대로, 정리만 스크립트로).

그림의 색·윤곽은 건드리지 않는다. 하는 일:
1. 가로 시트를 칸으로 자른다 (SpriteCook 애니메이션은 모든 칸이 같은 캔버스·같은 카메라)
2. 작은 떨어진 덩어리(따라 그려진 공 등)를 지운다 (--drop_small, 픽셀 수 기준)
3. 모든 칸을 **같은 상자**로 자른다 — 칸끼리 상대 위치를 그대로 둬야 발이 미끄러지지 않는다
4. 정수로 줄인다 (down: 도트 원본은 1, 칠한 그림 시험만 box 로 줄임)
5. 기준 칸의 발 자리(바닥 줄에서 칠한 칸의 가운데)를 잰다
6. 게임이 쓰는 이름 칸 순서로 다시 늘어놓아 png + 메타 .gd 를 쓴다

정의는 scripts/sheets.json (시트 이름 → 원본·칸 수·이름 칸 지도·출력). 다시 만들기:

    .venv/bin/python scripts/tidy_sheet.py batter        # 하나
    .venv/bin/python scripts/tidy_sheet.py --all         # 전부

출력 이름 옆에 *_preview.png (3배, 발 자리 표시) 를 build 폴더에 같이 쓴다. pillow·numpy 필요.
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFS = os.path.join(ROOT, "scripts", "sheets.json")


def drop_small(alpha, min_px):
    """이웃(상하좌우)으로 이어진 덩어리 중 min_px 보다 작은 것을 지운 alpha 판을 돌려준다."""
    h, w = alpha.shape
    seen = np.zeros_like(alpha, bool)
    out = alpha.copy()
    ys, xs = np.nonzero(alpha)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]:
            continue
        stack = [(y0, x0)]
        seen[y0, x0] = True
        blob = []
        while stack:
            y, x = stack.pop()
            blob.append((y, x))
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < h and 0 <= nx < w and alpha[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        if len(blob) < min_px:
            for y, x in blob:
                out[y, x] = False
    return out


def tidy(name, d):
    src = Image.open(os.path.join(ROOT, d["src"])).convert("RGBA")
    n = d["frames"]
    fw = src.width // n
    frames = [np.array(src.crop((i * fw, 0, (i + 1) * fw, src.height))) for i in range(n)]
    if d.get("drop_small"):
        for f in frames:
            keep = drop_small(f[:, :, 3] > 0, d["drop_small"])
            f[~keep] = 0
    # 모든 칸을 덮는 한 상자 (+1 여백)
    union = np.zeros(frames[0].shape[:2], bool)
    for f in frames:
        union |= f[:, :, 3] > 0
    ys, xs = np.nonzero(union)
    x0, x1 = max(xs.min() - 1, 0), min(xs.max() + 2, fw)
    y0, y1 = max(ys.min() - 1, 0), min(ys.max() + 2, src.height)
    down = int(d.get("down", 1))
    # 줄인 뒤에도 칸이 정수 크기가 되게 상자를 down 의 배수로 넓힌다
    x1 = x0 + -(-(x1 - x0) // down) * down
    y1 = y0 + -(-(y1 - y0) // down) * down
    resample = Image.BOX if d.get("resample") == "box" else Image.NEAREST
    cells = []
    for f in frames:
        im = Image.fromarray(np.ascontiguousarray(f[y0:y1, x0:x1]))
        if down > 1:
            im = im.resize((im.width // down, im.height // down), resample)
            a = np.array(im)
            a[:, :, 3] = np.where(a[:, :, 3] >= 128, 255, 0)  # 반투명 가장자리 없이 (도트는 켜짐/꺼짐)
            im = Image.fromarray(a)
        cells.append(np.array(im))
    ch, cw = cells[0].shape[:2]
    # 발 자리: 기준 칸 바닥에서 위로 foot_rows 줄 안의 칠한 칸 가운데
    ref = cells[d.get("anchor_frame", 0)][:, :, 3] > 0
    rows = np.nonzero(ref.any(1))[0]
    bottom = int(rows.max())
    band = ref[max(bottom - d.get("foot_rows", 3) + 1, 0):bottom + 1]
    bx = np.nonzero(band.any(0))[0]
    feet = (int(round((bx.min() + bx.max()) / 2)), bottom + 1)
    # 이름 칸 순서로 늘어놓기
    names = list(d["map"].keys())
    sheet = np.zeros((ch, cw * len(names), 4), np.uint8)
    for j, nm in enumerate(names):
        i = d["map"][nm]
        assert 0 <= i < n, f"{name}.{nm}: 원본 칸 {i} 없음 (0~{n - 1})"
        sheet[:, j * cw:(j + 1) * cw] = cells[i]
    out_png = os.path.join(ROOT, d["png"])
    Image.fromarray(sheet).save(out_png)
    order = ", ".join(f'"{nm}": {j}' for j, nm in enumerate(names))
    meta = (
        f"class_name {d['class']}\n"
        f"## scripts/tidy_sheet.py {name} 가 만든다 (정의 scripts/sheets.json). 손으로 고치지 않는다.\n"
        f"## {d['png']} 의 칸 정보. 원본: {d['src']} (SpriteCook {d.get('asset_id', '?')})\n"
        f"const CELL := Vector2({cw}, {ch})\n"
        f"const FEET := Vector2({feet[0]}, {feet[1]})\n"
        f"const FRAMES := {{{order}}}\n"
        f"## 경기장 도트 1칸에 이 그림 몇 칸이 들어가나 (2 = 경기장보다 두 배 촘촘한 고해상도 도트 → 화면 배율은 PixelArt.unit 의 1/2)\n"
        f"const DENSITY := {int(d.get('density', 1))}\n"
    )
    # 원본 칸 좌표로 적은 점(공 놓는 손 등) → 발 자리 기준 칸 좌표 (줄인 뒤)
    for key, (px_, py_) in d.get("points", {}).items():
        meta += f"const {key} := Vector2({(px_ - x0) / down - feet[0]:g}, {(py_ - y0) / down - feet[1]:g})\n"
    with open(os.path.join(ROOT, d["meta"]), "w", encoding="utf-8") as f:
        f.write(meta)
    # 미리보기: 원본 칸 순서, 3배, 발 자리 빨간 점
    prev = np.zeros((ch, cw * n, 4), np.uint8)
    for i, c in enumerate(cells):
        prev[:, i * cw:(i + 1) * cw] = c
    bg = Image.new("RGBA", (cw * n, ch), (120, 170, 110, 255))
    bg.alpha_composite(Image.fromarray(prev))
    px = bg.load()
    for i in range(n):
        for dx in (-1, 0, 1):
            x = i * cw + feet[0] + dx
            if 0 <= x < bg.width:
                px[x, min(feet[1], ch - 1)] = (255, 0, 0, 255)
    os.makedirs(os.path.join(ROOT, "godot", "build"), exist_ok=True)
    bg.resize((bg.width * 3, bg.height * 3), Image.NEAREST).save(
        os.path.join(ROOT, "godot", "build", f"{name}_preview.png"))
    print(f"{name}: {len(names)}칸 {cw}x{ch} 발 {feet} → {d['png']}")


def tidy_still(name, d):
    """한 장짜리 그림(배경 등): 원본에서 crop 을 잘라 canvas 크기 판의 place 자리에 놓고, patches 로 작은 곳을 덮는다.
    그림은 줄이거나 늘리지 않는다(칸 그대로). patches: [[원본 판의 [x0, y0, x1, y1], 붙일 [x, y]], ...] — canvas 좌표.
    판 밖으로 남는 빈 칸은 fill_edges 면 가장자리 열을 되풀이해 채운다."""
    src = Image.open(os.path.join(ROOT, d["src"])).convert("RGB")
    piece = src.crop(tuple(d["crop"]))
    cw, ch = d["canvas"]
    out = Image.new("RGB", (cw, ch))
    px, py = d["place"]
    out.paste(piece, (px, py))
    if d.get("fill_edges"):
        for x in range(0, px):
            out.paste(out.crop((px + (x % 8), 0, px + (x % 8) + 1, ch)), (x, 0))
        for x in range(px + piece.width, cw):
            k = px + piece.width - 8 + (x % 8)
            out.paste(out.crop((k, 0, k + 1, ch)), (x, 0))
    for (x0, y0, x1, y1), (dx, dy) in d.get("patches", []):
        out.paste(out.crop((x0, y0, x1, y1)), (dx, dy))
    if d.get("palette"):
        a = np.array(out.convert("RGBA"))
        out = Image.fromarray(reduce_colors(a, d["palette"])).convert("RGB")
    out.save(os.path.join(ROOT, d["png"]))
    out.resize((cw * 3, ch * 3), Image.NEAREST).save(os.path.join(ROOT, "godot", "build", f"{name}_preview.png"))
    print(f"{name}: {cw}x{ch} → {d['png']}")


def reduce_colors(a, n):
    """잡점 줄이기 (2026-11-07 유저: 도트로 확정, 잡점 줄이기): 칠한 칸의 색을 n 개로 줄이고(섞어 찍기 없음),
    상하좌우 네 칸이 모두 같은 색인데 가운데만 다른 외톨이 점을 그 색으로 메운다. 투명 칸은 그대로."""
    # 투명 칸(검게 저장됨)이 색 계산에 끼면 작은 갈색 같은 색이 검게 끌려간다 → 칠한 칸의 색 하나로 채운 뒤 줄인다
    solid0 = a[:, :, 3] > 0
    src = a[:, :, :3].copy()
    if solid0.any():
        src[~solid0] = src[solid0][0]
    rgb = Image.fromarray(src)
    q = np.array(rgb.quantize(colors=n, method=Image.Quantize.FASTOCTREE, kmeans=3, dither=Image.Dither.NONE).convert("RGB"))  # 옥트리: 작은 면의 색(갈색 글러브)도 지킨다
    out = a.copy()
    out[:, :, :3] = q
    solid = a[:, :, 3] > 0
    up, dn, lf, rt = q[:-2, 1:-1], q[2:, 1:-1], q[1:-1, :-2], q[1:-1, 2:]
    s4 = solid[:-2, 1:-1] & solid[2:, 1:-1] & solid[1:-1, :-2] & solid[1:-1, 2:] & solid[1:-1, 1:-1]
    same = (up == dn).all(-1) & (up == lf).all(-1) & (up == rt).all(-1)
    lone = same & ~(q[1:-1, 1:-1] == up).all(-1) & s4
    inner = out[1:-1, 1:-1]
    inner[lone, :3] = up[lone]
    return out


def recolor_team(a, hue_to):
    """팀 색 바꾸기: 파랑 계열(색상 190~260도, 채도 0.3 이상) 칸만 색상을 hue_to 로 돌린다. 밝기·채도는 그대로.
    (셰이더로 하려던 팀 색 바꾸기를 미리 구워 둔다 — 그림의 다른 색은 건드리지 않는다)"""
    import colorsys
    out = a.copy()
    h, w = a.shape[:2]
    for y in range(h):
        for x in range(w):
            r, g, b, al = a[y, x]
            if al == 0:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if 190 / 360 <= hh <= 260 / 360 and ss >= 0.3:
                rr, gg, bb = colorsys.hls_to_rgb(hue_to / 360, ll, ss)
                out[y, x, :3] = (round(rr * 255), round(gg * 255), round(bb * 255))
    return out


def tidy_grid(name, d):
    """격자 한 장(cols×rows, 칸마다 따로 그린 동작)을 한 줄 시트로: 칸마다 발 자리(바닥 줄·바닥 띠의 가운데)를 맞춰
    모든 칸을 같은 크기·같은 발 자리로 놓는다. variants 의 k 마다 1/k 로 줄인 판(box, 반투명 없음)도 쓴다 — 멀리 있는 선수용.
    them 이 있으면 팀 색을 바꾼 판(*_them)도 쓴다."""
    src = np.array(Image.open(os.path.join(ROOT, d["src"])).convert("RGBA"))
    cols, rows = d["cols"], d["rows"]
    gw, gh = src.shape[1] // cols, src.shape[0] // rows
    figs = []
    for j in range(rows):
        for i in range(cols):
            f = src[j * gh:(j + 1) * gh, i * gw:(i + 1) * gw].copy()
            if d.get("drop_small"):
                keep = drop_small(f[:, :, 3] > 0, d["drop_small"])
                f[~keep] = 0
            al = f[:, :, 3] > 0
            ys, xs = np.nonzero(al)
            bottom = int(ys.max())
            if d.get("anchor") == "centroid":
                # 칸이 바뀌어도 몸이 옆으로 밀려 보이지 않게 가로 기준점 = 칠한 칸 전체의 무게중심 (2026-11-07 유저)
                fx = int(round(xs.mean()))
            else:
                band = al[max(bottom - d.get("foot_rows", 3) + 1, 0):bottom + 1]
                bx = np.nonzero(band.any(0))[0]
                fx = int(round((bx.min() + bx.max()) / 2))
            figs.append((f, fx, bottom + 1, xs.min(), xs.max(), ys.min()))
    left = max(fx - x0 for _, fx, _, x0, _, _ in figs)
    right = max(x1 + 1 - fx for _, fx, _, _, x1, _ in figs)
    up = max(fb - y0 for _, _, fb, _, _, y0 in figs)
    m = 1
    for k in d.get("variants", [1]):
        m = m * k // __import__("math").gcd(m, k)
    # 발 자리·칸 크기를 m(줄인 판 배율들의 최소공배수)의 배수로 — 줄인 판에서도 발 자리가 정수 칸이다
    fx0 = int(-(-left // m) * m)
    cw = int(-(-(fx0 + right) // m) * m)
    ch = int(-(-up // m) * m)
    feet = (fx0, ch)
    names = d["names"]
    sheet = np.zeros((ch, cw * len(names), 4), np.uint8)
    for n, (f, fx, fb, *_ ) in enumerate(figs[:len(names)]):
        ox = n * cw + feet[0] - fx
        oy = feet[1] - fb
        for y in range(f.shape[0]):
            ty = y + oy
            if not 0 <= ty < ch:
                continue
            for x in range(f.shape[1]):
                tx = x + ox
                if f[y, x, 3] and n * cw <= tx < (n + 1) * cw:
                    sheet[ty, tx] = f[y, x]
    if d.get("palette"):
        sheet = reduce_colors(sheet, d["palette"])
    base = os.path.join(ROOT, d["png"])
    stem = base[:-4]
    sheets = {"": sheet}
    if d.get("them"):
        sheets["_them"] = recolor_team(sheet, d["them"])
    for suffix, sh in sheets.items():
        for k in d.get("variants", [1]):
            im = Image.fromarray(sh)
            if k > 1:
                im = im.resize((im.width // k, im.height // k), Image.BOX)
                a = np.array(im)
                a[:, :, 3] = np.where(a[:, :, 3] >= 110, 255, 0)
                im = Image.fromarray(a)
            im.save(f"{stem}{suffix}{'' if k == 1 else '_s%d' % k}.png")
    order = ", ".join(f'"{nm}": {j}' for j, nm in enumerate(names))
    meta = (
        f"class_name {d['class']}\n"
        f"## scripts/tidy_sheet.py {name} 가 만든다 (정의 scripts/sheets.json). 손으로 고치지 않는다.\n"
        f"## {d['png']} (+ 팀 색 바꾼 *_them, 1/k 로 줄인 *_sK) 의 칸 정보. 원본: {d['src']} (SpriteCook {d.get('asset_id', '?')})\n"
        f"const CELL := Vector2({cw}, {ch})\n"
        f"const FEET := Vector2({feet[0]}, {feet[1]})\n"
        f"const FRAMES := {{{order}}}\n"
        f"## 줄인 판의 배율 (1/k). 칸 크기·발 자리는 k 로 나누면 정수다\n"
        f"const VARIANTS := {d.get('variants', [1])}\n"
        f"## 경기장 도트 1칸에 이 그림 몇 칸 (화면 배율 = PixelArt.unit / DENSITY)\n"
        f"const DENSITY := {int(d.get('density', 1))}\n"
    )
    with open(os.path.join(ROOT, d["meta"]), "w", encoding="utf-8") as f:
        f.write(meta)
    prev = Image.new("RGBA", (sheet.shape[1], ch * len(sheets)), (120, 170, 110, 255))
    for r, sh in enumerate(sheets.values()):
        prev.alpha_composite(Image.fromarray(sh), (0, r * ch))
    prev.resize((prev.width * 3, prev.height * 3), Image.NEAREST).save(os.path.join(ROOT, "godot", "build", f"{name}_preview.png"))
    print(f"{name}: {len(names)}칸 {cw}x{ch} 발 {feet} 판 {list(sheets)} × {d.get('variants', [1])} → {d['png']}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--all", action="store_true")
    a = ap.parse_args()
    defs = json.load(open(DEFS, encoding="utf-8"))
    defs = {k: v for k, v in defs.items() if not k.startswith("_")}
    names = list(defs) if a.all else a.names
    if not names:
        sys.exit("시트 이름을 주거나 --all: " + ", ".join(defs))
    for nm in names:
        if defs[nm].get("kind") == "still":
            tidy_still(nm, defs[nm])
        elif defs[nm].get("kind") == "grid":
            tidy_grid(nm, defs[nm])
        else:
            tidy(nm, defs[nm])


if __name__ == "__main__":
    main()
