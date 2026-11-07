#!/usr/bin/env python3
"""컷신 정지 그림 가져오기 (2026-11-05): 외부에서 만든 장면 그림(docs/refs/cutscene/*.png, Gemini)을 컷신 판으로 가공한다.

하는 일 (그림마다): 오른쪽 아래 생성 도구 표시(반짝이)를 지운다 — 반투명 흰색을 걷어 내 원래 색으로 되돌린다.
크기는 줄이지 않는다 (2026-11-05 유저: 98칸으로 줄이니 화질이 깨진다 → 원본 해상도 그대로, 화면 가운데 TV 틀에 띄운다).
나온 그림은 godot/art/cutscene_stills/<이름>.png. 컷신 정의의 컷에 "still": "<이름>" 으로 쓴다 (CutscenePlan · CutsceneView).

인물 기준 그림(char_*.png)은 그리지 않는다 — Gemini 에 첨부하는 참고용이다.
그림을 넣거나 고치면:  python3 scripts/import_cutscene_stills.py  → python3 scripts/make_cutscene_art.py
에셋 출처는 licenses/assets.md (원칙 6).
"""
import glob
import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..")
SRC = os.path.join(ROOT, "docs", "refs", "cutscene")
OUT = os.path.join(ROOT, "godot", "art", "cutscene_stills")

# 생성 도구 표시 (2026-11-05 바꿈): Gemini 반짝이는 흰색을 반투명하게 얹은 것이라, 그 투명도 판(scripts/gemini_mark_alpha.png,
# 1024×434 그림 기준 MARK_BOX 자리)을 알면 얹기 전 색을 되돌려 낼 수 있다: 원래 색 = (보이는 색 − α·255) / (1 − α).
# 투명도 판은 바탕이 고른 그림 넷에서 잰 값의 중앙값이다. 옛 방식(위 띠를 복사해 덮기)은 배경이 복잡하면 티가 났다.
MARK_BOX = (905, 315, 985, 395)
MARK_BASE = (1024, 434)
ALPHA = os.path.join(os.path.dirname(__file__), "gemini_mark_alpha.png")


def erase_mark(im):
    """반짝이의 반투명 흰색을 걷어 낸다 (그림 크기가 다르면 자리·판을 비율대로 늘린다)"""
    w, h = im.size
    sx, sy = w / MARK_BASE[0], h / MARK_BASE[1]
    box = (round(MARK_BOX[0] * sx), round(MARK_BOX[1] * sy), round(MARK_BOX[2] * sx), round(MARK_BOX[3] * sy))
    alpha = Image.open(ALPHA).convert("L").resize((box[2] - box[0], box[3] - box[1]), Image.BILINEAR)
    a = np.asarray(alpha).astype(float)[..., None] / 255.0
    crop = np.asarray(im.crop(box)).astype(float)
    fixed = np.clip((crop - a * 255.0) / np.maximum(1.0 - a, 0.3), 0, 255)
    patch = Image.fromarray(fixed.round().astype(np.uint8))
    # 가장자리에 남는 옅은 테두리는 반짝이 자리 안에서만 3칸 중앙값으로 고른다
    soft = patch.filter(ImageFilter.MedianFilter(3))
    edge = alpha.point(lambda v: 255 if v > 0 else 0).filter(ImageFilter.MaxFilter(3))
    patch.paste(soft, (0, 0), edge)
    im.paste(patch, box[:2])
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
