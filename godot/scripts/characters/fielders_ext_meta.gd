class_name FieldersExtMeta
## scripts/tidy_sheet.py fielders 가 만든다 (정의 scripts/sheets.json). 손으로 고치지 않는다.
## godot/art/pixel/fielders_ext.png (+ 팀 색 바꾼 *_them, 1/k 로 줄인 *_sK) 의 칸 정보. 원본: docs/refs/sprites/fielders_sd_grid_v1.png (SpriteCook a5d7e2ab-6d8b-4be1-82d7-4a0c4a269504)
const CELL := Vector2(48, 48)
const FEET := Vector2(24, 48)
const FRAMES := {"ready": 0, "run_r1": 1, "run_r2": 2, "run_r3": 3, "away1": 4, "away2": 5, "toward1": 6, "toward2": 7, "grounder": 8, "fly": 9, "throw": 10, "dive": 11, "lead": 12, "slide": 13, "onbase": 14, "jog": 15}
## 줄인 판의 배율 (1/k). 칸 크기·발 자리는 k 로 나누면 정수다
const VARIANTS := [1, 2, 3, 4]
## 경기장 도트 1칸에 이 그림 몇 칸 (화면 배율 = PixelArt.unit / DENSITY)
const DENSITY := 3
