"""타석 경기장 일러스트(docs/refs/sprites/batting_stadium_2d_v2.png)를 게임 배경 판으로 옮긴다.

원본을 늘리거나 줄이지 않는다 (가로세로 같은 배율, 원본 픽셀 그대로). 옛 판(340×130 칸)의 기준점
— 투수판·홈플레이트 높이 — 에 맞는 자리를 잘라 내고, 판이 원본보다 넓은 양옆은 가장자리 열을 늘여 채운다.
(2026-11-10 Codex 첫 적용본은 가로 0.92배·세로 구간마다 0.68~0.83배로 눌러 비율이 어긋났다.)

    .venv/bin/python scripts/pack_stadium_art.py
"""
from pathlib import Path

from PIL import Image

SRC = Path("docs/refs/sprites/batting_stadium_2d_v2.png")
OUT = Path("godot/art/illustrated/stadium.png")
FIELD = (340, 130)  # PixelMeta.FIELD_SIZE (칸)
# 원본 y → 판 y(칸): 마운드 506 → 74, 홈플레이트 795 → 122 (옛 판과 같은 자리)
SRC_MOUND, SRC_HOME = 506, 795
GRID_MOUND, GRID_HOME = 74, 122


def main() -> None:
    src = Image.open(SRC).convert("RGB")
    s = (GRID_HOME - GRID_MOUND) / (SRC_HOME - SRC_MOUND)  # 칸 / 원본 px
    top = round(SRC_HOME - GRID_HOME / s)
    w, h = round(FIELD[0] / s), round(FIELD[1] / s)
    band = src.crop((0, top, src.width, top + h))
    out = Image.new("RGB", (w, h))
    left = (w - src.width) // 2
    out.paste(band, (left, 0))
    # 양옆 빈 곳: 가장자리 한 열을 늘인다 (화면 비율이 아주 넓을 때만 보인다)
    out.paste(band.crop((0, 0, 1, h)).resize((left, h)), (0, 0))
    right = left + src.width
    out.paste(band.crop((src.width - 1, 0, src.width, h)).resize((w - right, h)), (right, 0))
    out.save(OUT)
    print(f"{OUT} {w}×{h} (원본 {src.width}×{src.height}, 위 {top}px 잘라냄, 칸당 {1 / s:.2f}px)")


if __name__ == "__main__":
    main()
