"""저장소 루트에서 실행해 경기 일러스트의 칸과 화면 좌표를 맞춘다."""
from pathlib import Path
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path("scripts").resolve()))
from tidy_sheet import recolor_team

REFS = Path("docs/refs/sprites")
ART = Path("godot/art/illustrated")
CELL = 512
PLAYER_HEIGHT = 320


def pack(name, sources):
    atlas = Image.new("RGBA", (CELL * 4, CELL * 4))
    index = 0
    for source, columns, rows, standing_height in sources:
        sheet = Image.open(REFS / f"{source}.png").convert("RGBA")
        scale = PLAYER_HEIGHT / standing_height
        for row in range(rows):
            for column in range(columns):
                cell = sheet.crop((
                    column * sheet.width // columns, row * sheet.height // rows,
                    (column + 1) * sheet.width // columns, (row + 1) * sheet.height // rows,
                ))
                bounds = cell.getchannel("A").point(lambda a: 255 if a > 100 else 0).getbbox()
                sprite = cell.crop(bounds)
                sprite = sprite.resize(
                    (round(sprite.width * scale), round(sprite.height * scale)),
                    Image.Resampling.LANCZOS,
                )
                x = (index % 4) * CELL + (CELL - sprite.width) // 2
                y = (index // 4 + 1) * CELL - sprite.height - 8
                atlas.paste(sprite, (x, y))
                index += 1
    atlas.save(ART / f"{name}.png")
    Image.fromarray(recolor_team(np.array(atlas), 5)).save(ART / f"{name}_them.png")


pack("fielder", [
    ("fielder_2d_poses_v1", 3, 3, 330),
])
# 달리기 전·후면 두 줄은 별도 시트에 둔다.
pack("fielder_run", [("fielder_2d_run_v1", 4, 2, 365)])
pack("runner", [
    ("runner_2d_actions_v1", 4, 3, 310),
    ("runner_2d_run_away_left_v1", 4, 1, 478),
])

# 원본 점수판, 마운드, 홈플레이트의 세로 위치를 기존 화면 격자에 맞춘다.
stadium = Image.open(REFS / "batting_stadium_2d_v1.png").convert("RGB")
out = Image.new("RGB", (1700, 650))
anchors = [(265, 0), (440, 135), (546, 240), (710, 370), (911, 610), (952, 650)]
for (top, y), (bottom, end) in zip(anchors, anchors[1:]):
    band = stadium.crop((0, top, 1592, bottom)).resize(
        (1700, end - y), Image.Resampling.LANCZOS,
    )
    out.paste(band, (0, y))
out.save(ART / "stadium.png")
