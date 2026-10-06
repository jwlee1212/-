class_name Tokens
extends RefCounted
## 디자인 토큰. 화면 코드는 색·간격·글자 크기·모서리를 여기서만 가져온다 (색상값을 화면 코드에 직접 쓰지 않는다).
## 캐주얼 톤이라 채도 높은 원색과 큰 모서리를 쓴다.

const BACKGROUND := Color("fff8ec")
const SURFACE := Color("ffffff")
const INK := Color("26324a")
const INK_SOFT := Color("6b7690")
const PRIMARY := Color("2f6bff")
const ACCENT := Color("ff8a1f")
const GOOD := Color("22b573")
const BAD := Color("ff4d5e")
const WARN := Color("ffc21a")
## 야구장
const GRASS := Color("4cc46a")
const DIRT := Color("e2a66b")
const CHALK := Color("ffffff")
const BAT := Color("8b5a2b")
const MITT := Color("6b3f1d")
## 중계 화면: 파울 지역 잔디, 담장, 관중석, 유니폼 (우리 팀 / 상대 팀)
const FOUL_GRASS := Color("3fae5c")
const WALL := Color("1f6b3a")
const STANDS := Color("8fa3c7")
const UNIFORM_US := Color("2f6bff")
const UNIFORM_THEM := Color("ff4d5e")
## 타석 경기장: 하늘(위→지평선), 관중석, 관중 점 색, 조명, 전광판, 밝은 잔디 줄무늬, 헬멧
const SKY_TOP := Color("3a7bd5")
const SKY_BOTTOM := Color("ffb26b")
const STANDS_DARK := Color("4b5d82")
const CROWD: Array[Color] = [Color("f4f1ea"), Color("ffcf6b"), Color("ff7a7a"), Color("7fb2ff"), Color("9be3b0")]
const LIGHT := Color("fff7d6")
const SCOREBOARD := Color("1b2333")
const GRASS_LIGHT := Color("5ed27b")
const HELMET := Color("1d3f9e")
const SKIN := Color("ffd2a6")
## HUD: 반투명 유리 패널, 테두리, 글자, 볼카운트 점(볼·스트라이크·아웃), 구속 표시
const HUD_GLASS := Color(0.06, 0.09, 0.19, 0.62)
const HUD_BORDER := Color(1, 1, 1, 0.18)
const HUD_TEXT := Color("ffffff")
const HUD_TEXT_SOFT := Color(1, 1, 1, 0.72)
const COUNT_BALL := Color("34d399")
const COUNT_STRIKE := Color("fbbf24")
const COUNT_OUT := Color("f87171")
const LCD_BG := Color("0b1220")
const LCD_TEXT := Color("7cffb2")
## 구종 색 (구종이 드러났을 때 공 테두리·이름). 목록에 없는 구종은 PRIMARY
const PITCH_COLORS := {"fastball": PRIMARY, "slider": ACCENT, "changeup": GOOD}
## 공·타구 그림자, 겹쳐 그리는 반투명 바탕
const SHADOW := Color(0, 0, 0, 0.25)
const SCRIM := Color(0, 0, 0, 0.6)
const CONFETTI: Array[Color] = [PRIMARY, ACCENT, GOOD, WARN, BAD]

const SPACE_XS := 4
const SPACE_SM := 8
const SPACE_MD := 16
const SPACE_LG := 24

const RADIUS_CARD := 20
const RADIUS_HUD := 16

const FONT_CAPTION := 12
const FONT_BODY := 15
const FONT_LABEL := 15
const FONT_TITLE := 22
const FONT_HERO := 34

const FONT_REGULAR := preload("res://fonts/pretendard_regular.otf")
const FONT_BOLD := preload("res://fonts/pretendard_bold.otf")
