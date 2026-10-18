#!/usr/bin/env python3
"""캐릭터 포즈 그림 자르기: 초록 배경 원본 → godot/art/characters/<캐릭터>/<포즈>.png  (캐릭터: batter, pitcher)

원본은 godot/art/characters/<캐릭터>/source/<포즈>.png (유저가 Gemini 로 만든 그림, licenses/assets.md).
  python3 -m venv /tmp/v && /tmp/v/bin/pip install pillow numpy scipy
  /tmp/v/bin/python scripts/cut_character_poses.py

하는 일: 초록 배경을 투명하게 빼고, 가장 큰 덩어리(캐릭터)만 남겨 꼭 맞게 자른다 (워터마크 ✦ 는 떨어진 덩어리라 빠진다).
자른 크기와 원본에서의 자리를 출력한다 — batter_sprite.gd · pitcher_poses.gd 의 FRAMES 에 발 자리·키를 적을 때 쓴다.
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent / "godot/art/characters"
CHARACTERS = ["batter", "pitcher"]


def cut(path: Path, out: Path) -> None:
    rgb = np.asarray(Image.open(path).convert("RGB")).astype(float)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    alpha = 1.0 - np.clip((g - np.maximum(r, b)) / 90.0, 0.0, 1.0)
    # 반투명 가장자리는 초록 번짐을 걷어 낸다
    g = np.where(alpha < 1.0, np.minimum(g, np.maximum(r, b)), g)
    labels, count = ndimage.label(alpha > 0.5)
    sizes = ndimage.sum(np.ones_like(labels), labels, range(1, count + 1))
    biggest = int(np.argmax(sizes)) + 1
    keep = ndimage.binary_dilation(labels == biggest, iterations=3)
    rgba = np.dstack([r, g, b, np.where(keep, alpha, 0.0) * 255.0]).astype(np.uint8)
    box = ndimage.find_objects((labels == biggest).astype(int))[0]
    y0, y1, x0, x1 = box[0].start, box[0].stop, box[1].start, box[1].stop
    Image.fromarray(rgba[y0:y1, x0:x1]).save(out / path.name)
    print(f"{out.name}/{path.stem}: 크기 {x1 - x0}x{y1 - y0}, 원본에서 ({x0}, {y0})")


if __name__ == "__main__":
    for character in CHARACTERS:
        for source in sorted((ROOT / character / "source").glob("*.png")):
            cut(source, ROOT / character)
