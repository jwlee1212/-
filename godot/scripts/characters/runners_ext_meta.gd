class_name RunnersExtMeta
## scripts/tidy_sheet.py runners 가 만든다 (정의 scripts/sheets.json). 손으로 고치지 않는다.
## godot/art/pixel/runners_ext.png (+ 팀 색 바꾼 *_them, 1/k 로 줄인 *_sK) 의 칸 정보. 원본: docs/refs/sprites/runners_directions_headfirst_v1.png (SpriteCook ?)
const CELL := Vector2(288, 280)
const FEET := Vector2(128, 224)
const FRAMES := {"away_0": 0, "away_1": 1, "away_2": 2, "away_3": 3, "away_right_0": 4, "away_right_1": 5, "away_right_2": 6, "away_right_3": 7, "away_left_0": 8, "away_left_1": 9, "away_left_2": 10, "away_left_3": 11, "toward_right_0": 12, "toward_right_1": 13, "toward_right_2": 14, "toward_right_3": 15, "toward_left_0": 16, "toward_left_1": 17, "toward_left_2": 18, "toward_left_3": 19, "headfirst_0": 20, "headfirst_1": 21, "headfirst_2": 22, "headfirst_3": 23}
## 줄인 판의 배율 (1/k). 칸 크기·발 자리는 k 로 나누면 정수다
const VARIANTS := [1, 4, 8]
## 경기장 도트 1칸에 이 그림 몇 칸 (화면 배율 = PixelArt.unit / DENSITY)
const DENSITY := 1
