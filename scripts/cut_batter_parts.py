#!/usr/bin/env python3
"""타자 파츠 자르기: 초록 배경 원본 시트 → godot/art/characters/batter/*.png

원본(source/)은 유저가 Gemini 로 만든 그림이다 (licenses/assets.md).
  python3 -m venv /tmp/v && /tmp/v/bin/pip install pillow numpy scipy
  /tmp/v/bin/python scripts/cut_batter_parts.py

하는 일
1. 초록 배경을 투명하게 뺀다 (초록이 빨강·파랑보다 튀는 만큼 투명)
2. 서로 떨어진 덩어리를 조각으로 나눈다 (순서는 위→아래, 왼쪽→오른쪽으로 고정)
3. 팔은 위팔·아래팔이 붙어 나와서 남색 소매 끝에서 자른다. 아래팔의 잘린 끝에는
   같은 살색·테두리로 둥근 마개를 그려, 팔꿈치에서 돌려도 틈이 보이지 않게 한다
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

OUT = Path(__file__).resolve().parent.parent / "godot/art/characters/batter"
SRC = OUT / "source"
PAD = 4

# parts_sheet.png 에서 나오는 조각 번호 → 파일 이름 (안 쓰는 조각은 뺀다: 붙은 팔 2개, 굽힌 다리 2개 — 골반 그림에 허벅지가 이미 있어 겹친다)
SHEET_PARTS = {0: "head", 1: "torso", 4: "pelvis", 5: "glove_top", 6: "glove_bottom", 7: "bat", 8: "leg_back", 9: "leg_front"}
# arms_sheet.png 의 두 번째 조각(소매 달린 팔)을 자르는 높이 = 남색 소매가 끝나는 줄
ARM_PIECE = 0
ARM_CUT_Y = 292


def pieces(path: Path) -> list[Image.Image]:
    rgb = np.asarray(Image.open(path).convert("RGB")).astype(float)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    spill = g - np.maximum(r, b)
    alpha = 1.0 - np.clip(spill / 90.0, 0.0, 1.0)
    # 반투명 가장자리는 초록 번짐을 걷어 낸다
    g = np.where(alpha < 1.0, np.minimum(g, np.maximum(r, b)), g)
    rgba = np.dstack([r, g, b, alpha * 255.0]).astype(np.uint8)
    labels, _ = ndimage.label(alpha > 0.5)
    result = []
    for index, box in enumerate(ndimage.find_objects(labels)):
        height, width = box[0].stop - box[0].start, box[1].stop - box[1].start
        if height * width < 1500:  # 워터마크(✦)·잡티
            continue
        ys = slice(max(box[0].start - PAD, 0), box[0].stop + PAD)
        xs = slice(max(box[1].start - PAD, 0), box[1].stop + PAD)
        piece = rgba[ys, xs].copy()
        own = ndimage.binary_dilation(labels[ys, xs] == index + 1, iterations=3)
        piece[..., 3] = np.where(own, piece[..., 3], 0)
        result.append(Image.fromarray(piece))
    return result


def main() -> None:
    sheet = pieces(SRC / "parts_sheet.png")
    for index, name in SHEET_PARTS.items():
        sheet[index].save(OUT / f"{name}.png")

    arm = pieces(SRC / "arms_sheet.png")[ARM_PIECE]
    arm.crop((0, 0, arm.width, ARM_CUT_Y + 6)).save(OUT / "upper_arm.png")
    forearm = Image.new("RGBA", (arm.width, arm.height - ARM_CUT_Y + 70), (0, 0, 0, 0))
    skin = arm.getpixel((120, 380))
    line = arm.getpixel((66, 330))
    ImageDraw.Draw(forearm).ellipse((64, 4, 198, 138), fill=skin, outline=line, width=11)
    lower = arm.crop((0, ARM_CUT_Y + 8, arm.width, arm.height))
    forearm.paste(lower, (0, 78), lower)
    forearm.save(OUT / "forearm.png")

    for path in sorted(OUT.glob("*.png")):
        print(path.name, Image.open(path).size)


if __name__ == "__main__":
    main()
