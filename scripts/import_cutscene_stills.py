#!/usr/bin/env python3
"""컷신 정지 그림 가져오기 (2026-11-05): 외부에서 만든 장면 그림(docs/refs/cutscene/*.png, Gemini)을 컷신 판으로 가공한다.

하는 일 (그림마다): 오른쪽 아래 생성 도구 표시(반짝이)를 지운다 — 그 자리를 바로 위 띠로 덮는다.
크기는 줄이지 않는다 (2026-11-05 유저: 98칸으로 줄이니 화질이 깨진다 → 원본 해상도 그대로, 화면 가운데 TV 틀에 띄운다).
나온 그림은 godot/art/cutscene_stills/<이름>.png. 컷신 정의의 컷에 "still": "<이름>" 으로 쓴다 (CutscenePlan · CutsceneView).

인물 기준 그림(char_*.png)은 그리지 않는다 — Gemini 에 첨부하는 참고용이다.
그림을 넣거나 고치면:  python3 scripts/import_cutscene_stills.py  → python3 scripts/make_cutscene_art.py
에셋 출처는 licenses/assets.md (원칙 6).
"""
import glob
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
SRC = os.path.join(ROOT, "docs", "refs", "cutscene")
OUT = os.path.join(ROOT, "godot", "art", "cutscene_stills")

# 생성 도구 표시 자리 (원본 넓이·높이에 대한 비율: 왼쪽, 위, 오른쪽, 아래) — Gemini 는 오른쪽 아래 반짝이
MARK = (0.895, 0.78, 0.975, 0.905)


def erase_mark(im):
    """표시 자리를 바로 위의 같은 크기 띠로 덮는다 (땅·옷처럼 위아래로 이어지는 배경이 많다 — 왼쪽은 인물 그림자가 끼기 쉽다)"""
    w, h = im.size
    x0, y0, x1, y1 = int(MARK[0] * w), int(MARK[1] * h), int(MARK[2] * w), int(MARK[3] * h)
    patch = im.crop((x0, y0 - (y1 - y0), x1, y0))
    im.paste(patch, (x0, y0))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    done = []
    for path in sorted(glob.glob(os.path.join(SRC, "*.png"))):
        name = os.path.splitext(os.path.basename(path))[0]
        if name.startswith("char_"):
            continue
        im = erase_mark(Image.open(path).convert("RGB"))
        im.save(os.path.join(OUT, name + ".png"))
        done.append("%s %dx%d" % (name, *im.size))
    print("정지 그림 %d장: %s" % (len(done), ", ".join(done)))


if __name__ == "__main__":
    main()
