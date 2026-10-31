class_name PixelMeta
extends RefCounted
## scripts/make_pixel_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
## 좌표는 그림 픽셀(화면에서 SCALE 배), 발 자리 기준·위가 -.

const SCALE := 3
const FIELD_SIZE := Vector2(340, 130)
const BATTER_CELL := Vector2(150, 128)
const BATTER_FEET := Vector2(62, 122)
const BATTER := {"stance": 0, "breath": 1, "load_a": 2, "load": 3, "stride_up": 4, "stride": 5, "landing": 6, "rotate1": 7, "rotate2": 8, "contact_high": 9, "contact_mid": 10, "contact_low": 11, "extend": 12, "follow1": 13, "follow2": 14, "follow3": 15, "finish": 16, "bunt": 17, "recover": 18, "settle": 19}
const PITCHER_CELL := Vector2(44, 40)
const PITCHER_FEET := Vector2(22, 36)
## [와인드업 진행률, 칸] — 진행률이 넘으면 그 칸
const PITCHER_WINDUP := [[0, 0], [0.07, 1], [0.14, 2], [0.3, 3], [0.5, 4], [0.66, 5], [0.75, 6], [0.83, 7], [0.9, 8], [0.94, 9], [0.975, 10]]
## [공을 놓은 뒤 ms, 칸]
const PITCHER_AFTER := [[50, 11], [110, 12], [200, 13], [300, 14]]
## 공이 출발하는 손 자리 (공 놓는 칸)
const PITCHER_RELEASE := Vector2(-7, -22)
const CATCHER_CELL := Vector2(64, 44)
const CATCHER_FEET := Vector2(32, 44)
## 포수 그림에서 헬멧 꼭대기 (발 자리 기준)
const CATCHER_TOP := -40.0
const MITT_SIZE := Vector2(13, 13)
