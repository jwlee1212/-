#!/usr/bin/env python3
"""타자·투수·타석 배경만 재생성한다. 커리어 UI·골목 아트는 다루지 않는다.

실행: .venv/bin/python scripts/pack_batting_ink_art.py
"""
import json
import os
from pathlib import Path
import shutil

import numpy as np
from PIL import Image

from tidy_sheet import tidy

ROOT = Path(__file__).resolve().parent.parent


def main():
    os.chdir(ROOT)
    build = ROOT / "godot/build"
    build.mkdir(parents=True, exist_ok=True)
    source = Image.open(ROOT / "docs/refs/sprites/pitcher_ink_throw_v2.png").convert("RGBA")
    cells = []
    for row in range(4):
        for col in range(4):
            # 1254px 원본의 반 픽셀 경계를 반올림하고 314px 공통 판에 놓는다.
            cell = source.crop((round(col * source.width / 4), round(row * source.height / 4),
                                round((col + 1) * source.width / 4), round((row + 1) * source.height / 4)))
            canvas = Image.new("RGBA", (314, 314))
            canvas.paste(cell, (0, 0))
            array = np.array(canvas)
            array[array[:, :, 3] < 24] = 0
            cells.append(array)
    bottoms = [np.nonzero((cell[:, :, 3] >= 128).any(1))[0].max() for cell in cells]
    baseline = max(bottoms)
    strip = Image.new("RGBA", (314 * 16, 314 + int(max(baseline - bottom for bottom in bottoms))))
    for i, (cell, bottom) in enumerate(zip(cells, bottoms)):
        strip.paste(Image.fromarray(cell), (i * 314, int(baseline - bottom)))
    strip.save(build / "pitcher_ink_strip.png")

    definitions = json.loads((ROOT / "scripts/sheets.json").read_text())
    for name in ("batter", "pitcher"):
        tidy(name, definitions[name])
    shutil.copyfile(ROOT / "docs/mockups/stadium_ink_batting_v1.png",
                    ROOT / "godot/art/illustrated/stadium_ink.png")
    print("타자·투수·경기장 배경 정리 완료")


if __name__ == "__main__":
    main()
