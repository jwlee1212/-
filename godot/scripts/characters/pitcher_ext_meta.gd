class_name PitcherExtMeta
## scripts/tidy_sheet.py pitcher 가 만든다 (정의 scripts/sheets.json). 손으로 고치지 않는다.
## godot/art/pixel/pitcher_ext.png 의 칸 정보. 원본: docs/refs/sprites/pitcher_throw_v3_sheet.png (SpriteCook 3911c9de-f841-4f5d-a7bd-22ecca35e4d2)
const CELL := Vector2(59, 61)
const FEET := Vector2(30, 61)
const FRAMES := {"c0": 0, "c1": 1, "c2": 2, "c3": 3, "c4": 4, "c5": 5, "c6": 6, "c7": 7, "c8": 8, "c9": 9, "c10": 10, "c11": 11, "c12": 12, "c13": 13, "c14": 14}
## 경기장 도트 1칸에 이 그림 몇 칸이 들어가나 (2 = 경기장보다 두 배 촘촘한 고해상도 도트 → 화면 배율은 PixelArt.unit 의 1/2)
const DENSITY := 2
const RELEASE := Vector2(-12, -48)
