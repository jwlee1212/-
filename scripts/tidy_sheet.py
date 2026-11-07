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
        tidy(nm, defs[nm])


if __name__ == "__main__":
    main()
