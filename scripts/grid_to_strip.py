#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""격자 동작 시트(칠한 그림 · AI 생성) → tidy_sheet.py 가 읽는 가로 띠 (2026-11-10, ingame_hitter·ingame_pitcher).

2026-11-10 유저 결정: 인게임 캐릭터는 **고해상도 일러스트** — 줄이지 않고 원본 해상도 그대로, 테두리도 부드럽게 둔다
(도트로 줄였다가 화면에서 4~6배 키우면 깨진다). 게임은 부드러운 필터로 띄운다 (PixelArt.SMOOTH).

그림의 색·윤곽은 바꾸지 않는다. 하는 일:
1. 격자를 칸으로 자르고 거의 투명한 찌꺼기(alpha < 24)만 지운다 (생성기가 배경에 남긴 점). 나머지 반투명 테두리는 살린다
2. 칸마다 바닥(발끝)을 같은 줄에 맞춘다 — 생성기가 줄마다 사람을 조금씩 다른 높이에 그려서 그대로 두면 발이 들썩인다
3. HEIGHT 가 0 이면 줄이지 않는다 (기본). 0 보다 크면 그 키로 줄인다 (LANCZOS, 옛 도트 방식 시험용)
4. 칸을 가로로 늘어놓아 저장하고, 원본 칸 좌표로 적은 점(배트 스위트 스폿 등)을 그 칸 안 좌표로 바꿔 출력한다

    .venv/bin/python scripts/grid_to_strip.py SRC OUT COLS ROWS [HEIGHT] [frame:x,y ...]
"""
import sys
import numpy as np
from PIL import Image


def main():
    src, out, cols, rows = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    rest = sys.argv[5:]
    height = int(rest.pop(0)) if rest and ":" not in rest[0] else 0
    points = rest
    a = np.array(Image.open(src).convert("RGBA"))
    h, w = a.shape[:2]
    cw, ch = w // cols, h // rows
    cells = [a[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw].copy() for r in range(rows) for c in range(cols)]
    for c in cells:
        c[c[:, :, 3] < 24] = 0
    bottoms = [int(np.nonzero((c[:, :, 3] > 0).any(1))[0].max()) for c in cells]
    tops = [int(np.nonzero((c[:, :, 3] > 0).any(1))[0].min()) for c in cells]
    base = max(bottoms)
    shifts = [base - b for b in bottoms]
    tall = base - min(t + s for t, s in zip(tops, shifts)) + 1
    k = height / tall if height > 0 else 1.0
    pad = max(shifts)
    sw, sh = round(cw * k), round((ch + pad) * k)
    strip = Image.new("RGBA", (sw * len(cells), sh))
    for i, c in enumerate(cells):
        canvas = np.zeros((ch + pad, cw, 4), np.uint8)
        canvas[shifts[i]:shifts[i] + ch] = c
        im = Image.fromarray(canvas)
        if k != 1.0:
            im = im.resize((sw, sh), Image.LANCZOS)
        strip.paste(im, (i * sw, 0))
    strip.save(out)
    print(f"{out}: {len(cells)}칸 {sw}x{sh} 키 {round(tall * k)} (배율 {k:.3f}, 바닥 맞춤 {shifts})")
    for p in points:
        f, xy = p.split(":")
        x, y = map(float, xy.split(","))
        f = int(f)
        print(f"  점 칸{f} ({x:g},{y:g}) → 그 칸 안 ({round(x * k)}, {round((y + shifts[f]) * k)}) — sheets.json points 에 쓰는 값")


if __name__ == "__main__":
    main()
