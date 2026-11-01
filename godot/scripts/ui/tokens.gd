class_name Tokens
extends RefCounted
## 디자인 토큰. 화면 코드는 색·간격·글자 크기·모서리를 여기서만 가져온다 (색상값을 화면 코드에 직접 쓰지 않는다).
## 캐주얼 톤이라 채도 높은 원색과 큰 모서리를 쓴다.
## 색 배합 60-30-10 (2026-11-01): 배경 크림 60 · 카드 흰색 + 위·아래 바 남색 30 · 강조 주황 10.
## 강조색(ACCENT)은 "눌러야 할 것"(진행 버튼·알림 배지)에만 쓴다. 강조하고 싶은 글자는 NAVY·굵기로.

const BACKGROUND := Color("f4efe6")
const SURFACE := Color("ffffff")
const INK := Color("1e2a44")
const INK_SOFT := Color("7a8299")
const PRIMARY := Color("2f6bff")
const ACCENT := Color("ff6b2c")
## 위·아래 바(남색), 바 위에서 고른 탭 바탕, 바 위 흐린 글자
const NAVY := Color("1e2a44")
const NAVY_SOFT := Color("33415f")
const BAR_TEXT_SOFT := Color(1, 1, 1, 0.6)
const GOOD := Color("22b573")
const BAD := Color("ff4d5e")
const WARN := Color("ffc21a")
## 야구장
const GRASS := Color("4cc46a")
const DIRT := Color("e2a66b")
const CHALK := Color("ffffff")
## 중계 화면: 파울 지역 잔디, 담장, 관중석, 유니폼 (우리 팀 / 상대 팀)
const FOUL_GRASS := Color("3fae5c")
const WALL := Color("1f6b3a")
const STANDS := Color("8fa3c7")
const UNIFORM_US := Color("2f6bff")
const UNIFORM_THEM := Color("ff4d5e")
## 경기장: 관중석, 관중 점 색, 전광판, 밝은 잔디 줄무늬
const STANDS_DARK := Color("4b5d82")
const CROWD: Array[Color] = [Color("f4f1ea"), Color("ffcf6b"), Color("ff7a7a"), Color("7fb2ff"), Color("9be3b0")]
const SCOREBOARD := Color("1b2333")
const GRASS_LIGHT := Color("5ed27b")
## HUD: 반투명 유리 패널, 테두리, 글자, 볼카운트 점(볼·스트라이크·아웃)
const HUD_GLASS := Color(0.06, 0.09, 0.19, 0.62)
const HUD_BORDER := Color(1, 1, 1, 0.18)
const HUD_TEXT := Color("ffffff")
const HUD_TEXT_SOFT := Color(1, 1, 1, 0.72)
const COUNT_BALL := Color("34d399")
const COUNT_STRIKE := Color("fbbf24")
const COUNT_OUT := Color("f87171")
## 중계 그래픽 (타석 스코어 버그·투수 줄·구속·타자 자막): 짙은 남색 판, 공격 팀 줄 강조, 빈 점·베이스, 모서리
const BROADCAST_BG := Color(SCOREBOARD, 0.93)
const BROADCAST_ROW_HI := Color(1, 1, 1, 0.08)
const BROADCAST_EMPTY := Color(1, 1, 1, 0.22)
const RADIUS_BROADCAST := 6
## 구종 색 (구종이 드러났을 때 공 테두리·이름, 궤적 스크린샷). 목록에 없는 구종은 PRIMARY.
## 빠른 공은 파랑 계열, 옆으로 휘는 공은 주황 계열, 떨어지는 공은 초록·보라 계열
const PITCH_COLORS := {
	"four_seam": PRIMARY, "two_seam": Color("38b6ff"),
	"slider": ACCENT, "sweeper": BAD, "curve": Color("a55eea"),
	"changeup": GOOD, "forkball": Color("0f8a5f"), "splitter": WARN,
}
## 능력치 색 (선수 탭 막대, 스케줄 칸), 휴식 칸
## 채도를 낮춰 강조색(주황)과 다투지 않게 한다 (2026-11-01)
const STAT_COLORS := {"contact": Color("5b7db1"), "power": Color("c98a5e"), "eye": Color("5fa383"), "speed": Color("8e7cc3")}
const REST_COLOR := Color("6fa8c7")
## 스케줄 결과 도장 (대성공 · 성공 · 실패)
const OUTCOME_COLORS := {"great": Color("d99a1e"), "success": Color("4f9a74"), "fail": Color("d0566a")}
## 내 방 화면: 방 그림 위에 겹치는 반투명 흰 판, 아직 안 채운 칸
const ROOM_PANEL := Color(1, 1, 1, 0.9)
const ROOM_EMPTY := Color(1, 1, 1, 0.55)
## 방 그림 위에 덮는 크림색 막 (그림은 은은하게, 바탕은 배경색 — 60-30-10 의 60)
const ROOM_VEIL := Color(BACKGROUND, 0.82)
## 공·타구 그림자, 겹쳐 그리는 반투명 바탕
## 대화 화면: 배경 자리(그림이 없을 때), 일러스트 자리, 대사창, 지문 글자
const DIALOGUE_BG := Color("c9d6ef")
const DIALOGUE_BG_TEXT := Color(0.15, 0.2, 0.33, 0.35)
const PORTRAIT_SLOT := Color(1, 1, 1, 0.45)
const PORTRAIT_BORDER := Color("6b7690")
const DIALOGUE_BOX := Color(1, 1, 1, 1)
const NARRATION := Color("6b7690")
## 배경 그림 위에 얹는 옅은 막(인물이 돋보이게), 말하지 않는 인물에 곱하는 색(어둡게), 머리말 뒤 판
const DIALOGUE_BG_SCRIM := Color(0.05, 0.08, 0.16, 0.22)
const PORTRAIT_IDLE := Color(0.5, 0.52, 0.6, 1)
const DIALOGUE_HEADER_BG := Color(1, 1, 1, 0.86)
## 폰: 몸체(테두리), 화면 바탕, 받은·보낸 말풍선과 그 글자, 안 읽음 배지
const PHONE_BODY := Color("26324a")
const PHONE_SCREEN := Color("fff8ec")
const BUBBLE_THEM := Color("ffffff")
const BUBBLE_ME := Color("2f6bff")
const BUBBLE_ME_TEXT := Color("ffffff")
const BADGE := ACCENT
const SHADOW := Color(0, 0, 0, 0.25)
const SCRIM := Color(0, 0, 0, 0.6)
## 로비: 데모 타석 위에 메뉴를 얹는 오른쪽 판
const LOBBY_PANEL := Color(BACKGROUND, 0.93)
const CONFETTI: Array[Color] = [PRIMARY, ACCENT, GOOD, WARN, BAD]

const SPACE_XS := 4
const SPACE_SM := 8
const SPACE_MD := 16
const SPACE_LG := 24
const SPACE_XL := 32
## 최소 터치 영역 (보이는 버튼이 작아도 눌리는 자리는 이만큼), 홈 위 바·아래 탭 바 높이
const TOUCH_MIN := 48
const TOP_BAR_HEIGHT := 48
const BOTTOM_NAV_HEIGHT := 56
## 카드 모서리 (홈 카드·바)
const RADIUS_PANEL := 16
## 홈 양옆 칸 너비 (내 선수 카드 · 팀 기록)
const SIDE_COLUMN := 216

const RADIUS_CARD := 20
const RADIUS_HUD := 16
## 폰: 몸체 모서리·테두리 두께·너비, 옆에 서는 인물 자리 너비, 말풍선 모서리·최대 너비
const RADIUS_PHONE := 26
const PHONE_BEZEL := 6
const PHONE_WIDTH := 312
const PHONE_SIDE_WIDTH := 230
const RADIUS_BUBBLE := 12
## 알림 배지 지름, 허브 알림 띠 너비
const BADGE_SIZE := 16
const TOAST_WIDTH := 280
const BUBBLE_MAX_WIDTH := 196

const FONT_CAPTION := 12
const FONT_BODY := 15
const FONT_LABEL := 15
const FONT_TITLE := 22
const FONT_HERO := 34
## 로비 로고, 큰 숫자 (OVERALL 등)
const FONT_DISPLAY := 56
const FONT_NUMBER := 38
## 메뉴 화면: 영문 소제목 자간, 로고 기울기(글자 높이 대비 오른쪽으로 밀리는 비율), 구분선
const TRACKING_KICKER := 3
const TRACKING_WIDE := 7
const SLANT := 0.22
const DIVIDER := Color(INK_SOFT, 0.25)

const FONT_REGULAR := preload("res://fonts/pretendard_regular.otf")
const FONT_BOLD := preload("res://fonts/pretendard_bold.otf")


## 자간을 넓힌 글꼴 (영문 소제목: "KOREAN BASEBALL CAREER")
static func spaced(font: Font, px: int) -> FontVariation:
	var v := FontVariation.new()
	v.base_font = font
	v.spacing_glyph = px
	return v

