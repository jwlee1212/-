#!/usr/bin/env python3
"""타자 파츠 자르기: 초록 배경 원본 시트 → godot/art/characters/batter/*.png

원본(source/)은 유저가 Gemini 로 만든 그림이다 (licenses/assets.md).
  python3 -m venv /tmp/v && /tmp/v/bin/pip install pillow numpy scipy
  /tmp/v/bin/python scripts/cut_batter_parts.py

하는 일
1. 초록 배경을 투명하게 뺀다 (초록이 빨강·파랑보다 튀는 만큼 투명)
2. 서로 떨어진 덩어리를 조각으로 나눈다 (순서는 위→아래, 왼쪽→오른쪽으로 고정)
3. 몸통과 하체는 자세 그림(stance_reference.png)에서 따낸다 — 시트의 조각은 크기·각도가 자세 그림과 달랐다
4. 팔은 위팔·아래팔이 붙어 나와서 남색 소매 끝에서 자른다. 아래팔의 잘린 끝에는
   같은 살색·테두리로 둥근 마개를 그려, 팔꿈치에서 돌려도 틈이 보이지 않게 한다
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

OUT = Path(__file__).resolve().parent.parent / "godot/art/characters/batter"
SRC = OUT / "source"
PAD = 4

# parts_sheet.png 에서 나오는 조각 번호 → 파일 이름 (안 쓰는 조각은 뺀다: 몸통, 붙은 팔 2개, 골반, 다리 4개 — 몸통·하체는 자세 그림에서 가져온다)
SHEET_PARTS = {0: "head", 5: "glove_top", 6: "glove_bottom", 7: "bat"}
# arms_sheet.png 의 두 번째 조각(소매 달린 팔)을 자르는 높이 = 남색 소매가 끝나는 줄
ARM_PIECE = 0
ARM_CUT_Y = 292
# stance_reference.png 에서 하체(벨트~스파이크)를 통째로 자르는 상자와, 그 안에서 벨트 윗선이 지나는 두 점
# (이 선 위쪽은 상의·팔이라 지운다)
LOWER_BOX = (360, 600, 780, 890)
LOWER_BELT_LINE = ((60, 12), (330, 30))
# stance_reference.png 에서 몸통(등 + 가까운 쪽 소매)을 따내는 상자와 테두리 (그림 좌표).
# 위는 머리에, 오른쪽은 팔에 가려지는 자리에서 끊는다
TORSO_BOX = (425, 440, 700, 630)
TORSO_OUTLINE = [(430, 622), (430, 580), (440, 525), (462, 470), (490, 444), (590, 454), (600, 462), (690, 478),
                 (692, 560), (676, 566), (662, 622)]
# 오른쪽(소매 끝)과 오른쪽 아래는 그림에서 팔에 가려 테두리가 없다 — 이 선을 따라 테두리를 그려 넣는다
TORSO_ADDED_EDGE = [(688, 480), (689, 560), (676, 566), (662, 622)]


def keyed(path: Path) -> np.ndarray:
    """초록 배경을 뺀 RGBA 배열"""
    rgb = np.asarray(Image.open(path).convert("RGB")).astype(float)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    alpha = 1.0 - np.clip((g - np.maximum(r, b)) / 90.0, 0.0, 1.0)
    g = np.where(alpha < 1.0, np.minimum(g, np.maximum(r, b)), g)
    return np.dstack([r, g, b, alpha * 255.0]).astype(np.uint8)


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

    # 하체: 자세 그림에서 벨트 아래를 통째로 (골반 + 굽힌 두 다리가 한 장이라 이음새가 없다)
    x0, y0, x1, y1 = LOWER_BOX
    lower = keyed(SRC / "stance_reference.png")[y0:y1, x0:x1].copy()
    (ax, ay), (bx, by) = LOWER_BELT_LINE
    ys, xs = np.mgrid[0 : lower.shape[0], 0 : lower.shape[1]]
    line_y = ay + (by - ay) * (xs - ax) / (bx - ax)
    lower[..., 3] = np.where(ys < line_y, 0, lower[..., 3])
    Image.fromarray(lower).save(OUT / "lower_body.png")

    # 몸통: 자세 그림의 등을 테두리대로 따낸다 (하체와 같은 그림이라 크기·각도가 그대로 맞는다)
    x0, y0, x1, y1 = TORSO_BOX
    stance = keyed(SRC / "stance_reference.png")
    mask = Image.new("L", (stance.shape[1], stance.shape[0]), 0)
    ImageDraw.Draw(mask).polygon(TORSO_OUTLINE, fill=255)
    torso = stance.copy()
    torso[..., 3] = np.minimum(torso[..., 3], np.asarray(mask))
    torso_image = Image.fromarray(torso)
    # 테두리 색: 몸통 왼쪽 가장자리 줄에서 테두리 한가운데쯤
    first = int(np.argmax(stance[540, :, 3] > 250))
    edge_color = tuple(int(v) for v in stance[540, first + 3, :3]) + (255,)
    ImageDraw.Draw(torso_image).line(TORSO_ADDED_EDGE, fill=edge_color, width=7, joint="curve")
    torso_image.crop((x0, y0, x1, y1)).save(OUT / "torso.png")

    for path in sorted(OUT.glob("*.png")):
        print(path.name, Image.open(path).size)


if __name__ == "__main__":
    main()
