class_name BatterExtMeta
## scripts/tidy_sheet.py batter 가 만든다 (정의 scripts/sheets.json). 손으로 고치지 않는다.
## godot/art/pixel/batter_ext.png 의 칸 정보. 원본: docs/refs/sprites/batter_video_swing_hd_pair1_v1.png (SpriteCook ?)
const CELL := Vector2(673, 981)
const COLUMNS := 5
const FEET := Vector2(264, 980)
const FRAMES := {"stance": 0, "breath": 1, "load_a": 2, "load": 3, "stride_up": 4, "stride": 5, "landing": 6, "rotate1": 7, "rotate2": 8, "contact_high": 9, "contact_mid": 10, "contact_low": 11, "extend": 12, "follow1": 13, "follow2": 14, "follow3": 15, "finish": 16, "bunt": 17, "recover": 18, "settle": 19}
## 경기장 도트 1칸에 이 그림 몇 칸이 들어가나 (2 = 경기장보다 두 배 촘촘한 그림 → 화면 배율은 PixelArt.unit 의 1/2)
const DENSITY := 10.5269
const CONTACT := Vector2(350, -496)
