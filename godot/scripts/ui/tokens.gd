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
## 공·타구 그림자, 겹쳐 그리는 반투명 바탕
const SHADOW := Color(0, 0, 0, 0.25)
const SCRIM := Color(0, 0, 0, 0.6)
const CONFETTI: Array[Color] = [PRIMARY, ACCENT, GOOD, WARN, BAD]

const SPACE_XS := 4
const SPACE_SM := 8
const SPACE_MD := 16
const SPACE_LG := 24

const RADIUS_CARD := 20

const FONT_CAPTION := 12
const FONT_BODY := 15
const FONT_LABEL := 15
const FONT_TITLE := 22
const FONT_HERO := 34

const FONT_REGULAR := preload("res://fonts/pretendard_regular.otf")
const FONT_BOLD := preload("res://fonts/pretendard_bold.otf")
