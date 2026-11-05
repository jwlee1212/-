class_name PixelMeta
extends RefCounted
## scripts/make_pixel_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
## 좌표는 그림 픽셀(화면에서 SCALE 배), 발 자리 기준·위가 -.

const SCALE := 3
const FIELD_SIZE := Vector2(340, 130)
## 배경의 전광판 LED 판 (왼쪽 위·크기, 그림 픽셀) — 점수·볼카운트를 이 위에 도트로 찍는다
const BOARD_LED := Rect2(135, 27, 71, 21)
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
## 수비 화면 선수: 칸 크기·발 자리, 칸 순서 (팀마다 서기·달리기 둘, 우리 팀 먼저)
const FIELDER_CELL := Vector2(5, 8)
const FIELDER_FEET := Vector2(2, 7)
const FIELDER_FRAMES := 3
## 1루 클로즈업 (내 수비 송구): 배경 크기, 칸 크기·발 자리(투수와 같은 몸 뼈대), 칸 이름 → 번호
const CLOSEUP_BG := Vector2(256, 98)
const CLOSEUP_CELL := Vector2(44, 40)
const CLOSEUP_FEET := Vector2(22, 36)
const CLOSEUP := {"run_a": 0, "run_b": 1, "run_c": 2, "run_d": 3, "run_touch": 4, "run_pass": 5, "fb_ready": 6, "fb_reach": 7, "fb_stretch": 8, "fb_catch": 9, "fb_jump": 10, "ump_set": 11, "ump_out": 12, "ump_safe": 13}
## 배경 위 자리 (배경 그림 픽셀): 1루 베이스, 타자 주자가 달리는 줄(발 높이), 1루수·루심 발
const CLOSEUP_BAG := Vector2(128, 72)
const CLOSEUP_RUNNER_Y := 72
const CLOSEUP_FB_FOOT := Vector2(121, 78)
const CLOSEUP_UMP_FOOT := Vector2(78, 66)
## 타자 주자 베이스 터치 칸에서 뻗은 앞발 끝 (발 기준) — 이 발이 베이스에 닿는 순간이 1루를 밟는 순간
const CLOSEUP_TOUCH_FOOT := Vector2(-10, 0)
## 1루수 글러브 자리 (발 기준): 뻗어 잡을 때, 뛰어오를 때
const CLOSEUP_GLOVE := Vector2(-3, -8)
const CLOSEUP_GLOVE_JUMP := Vector2(5, -31)
