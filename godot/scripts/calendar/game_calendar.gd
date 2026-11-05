class_name GameCalendar
extends RefCounted
## 달력: 날짜 계산과 경기 날짜 (로직만 — 화면은 screens/calendar_view.gd).
## 날짜는 "1970-01-01 부터 며칠째"인 정수 하나로 다룬다 (비교·더하기가 쉽고 저장도 정수). 연·월·일 변환은
## 그레고리력 공식(civil_from_days)이라 시계를 읽지 않는다 — 같은 진행이면 같은 날짜다 (원칙 2·4).
##
## 경기 날짜 (balance.json career.calendar):
##   고교 — 학년마다 hsOpen 당일 또는 그 뒤 첫 토요일이 1주차, 주마다 토요일. 겨울 캠프는 hsCamp 뒤 첫 토요일부터.
##   프로 — proOpen 당일 또는 그 뒤 첫 화요일이 개막. 한 주 = 월요일 휴식(훈련·이야기) + 화~일 6경기.
##          포스트시즌은 정규시즌 다음 주부터 라운드마다 한 주 (화요일부터 하루 한 경기, 7차전은 월요일).

const WEEKDAYS := ["월", "화", "수", "목", "금", "토", "일"]
const MON := 0
const TUE := 1
const SAT := 5
const SUN := 6


static func days_from_civil(y: int, m: int, d: int) -> int:
	var yy := y - (1 if m <= 2 else 0)
	var era := (yy if yy >= 0 else yy - 399) / 400
	var yoe := yy - era * 400
	var mp := (m + 9) % 12
	var doy := (153 * mp + 2) / 5 + d - 1
	var doe := yoe * 365 + yoe / 4 - yoe / 100 + doy
	return era * 146097 + doe - 719468


## 날짜 번호 → {"y", "m", "d"}
static func civil_from_days(z: int) -> Dictionary:
	var zz := z + 719468
	var era := (zz if zz >= 0 else zz - 146096) / 146097
	var doe := zz - era * 146097
	var yoe := (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
	var doy := doe - (365 * yoe + yoe / 4 - yoe / 100)
	var mp := (5 * doy + 2) / 153
	var d := doy - (153 * mp + 2) / 5 + 1
	var m := mp + 3 if mp < 10 else mp - 9
	return {"y": yoe + era * 400 + (1 if m <= 2 else 0), "m": m, "d": d}


## 요일: 0 월 … 6 일 (1970-01-01 은 목요일)
static func weekday(z: int) -> int:
	return posmod(z + 3, 7)


## z 당일 또는 그 뒤 첫 wd 요일
static func on_or_after(z: int, wd: int) -> int:
	return z + posmod(wd - weekday(z), 7)


static func days_in_month(y: int, m: int) -> int:
	return days_from_civil(y + (1 if m == 12 else 0), 1 if m == 12 else m + 1, 1) - days_from_civil(y, m, 1)


## "4월 12일 (토)" · with_year 면 "2030년 4월 12일 (토)"
static func text(z: int, with_year: bool = false) -> String:
	var c := civil_from_days(z)
	var t := "%d월 %d일 (%s)" % [c["m"], c["d"], WEEKDAYS[weekday(z)]]
	return ("%d년 " % c["y"]) + t if with_year else t


## "4/12"
static func short(z: int) -> String:
	var c := civil_from_days(z)
	return "%d/%d" % [c["m"], c["d"]]


static func _md(cfg: CareerConfig, key: String, y: int) -> int:
	var md: Array = cfg.calendar[key]
	return days_from_civil(y, int(md[0]), int(md[1]))


# ---------- 고교 ----------

## 고교 grade 학년의 달력 해
static func hs_year(cfg: CareerConfig, grade: int) -> int:
	return int(cfg.calendar["firstYear"]) + grade - 1


## 고교 grade 학년 week 주차 경기(토요일)
static func hs_game_day(cfg: CareerConfig, grade: int, week: int) -> int:
	return on_or_after(_md(cfg, "hsOpen", hs_year(cfg, grade)), SAT) + 7 * (week - 1)


## 고교 grade 학년 겨울 캠프 camp_week 주차 (토요일)
static func hs_camp_day(cfg: CareerConfig, grade: int, camp_week: int) -> int:
	return on_or_after(_md(cfg, "hsCamp", hs_year(cfg, grade)), SAT) + 7 * (camp_week - 1)


## 고교 커리어의 "오늘" (이번 주 경기 날 — 캠프 중이면 캠프 주)
static func hs_today(career: CareerState) -> int:
	var cfg := career.cfg
	if career.in_camp() or career.camp_finished():
		return hs_camp_day(cfg, career.player.grade, maxi(1, mini(career.camp_week, int(cfg.camp["weeks"]))))
	return hs_game_day(cfg, career.player.grade, career.week)


# ---------- 프로 ----------

## 프로 개막일 (그 해 proOpen 당일 또는 그 뒤 첫 화요일)
static func pro_opening(cfg: CareerConfig, year: int) -> int:
	return on_or_after(_md(cfg, "proOpen", year), TUE)


## 프로 week 주차 d 번째 경기 날 (d 0 화 … 5 일, 포스트시즌 7차전은 6 월)
static func pro_day(cfg: CareerConfig, year: int, week: int, d: int) -> int:
	return pro_opening(cfg, year) + 7 * (week - 1) + d


## 프로 week 주차의 월요일 (휴식·훈련일)
static func pro_monday(cfg: CareerConfig, year: int, week: int) -> int:
	return pro_opening(cfg, year) + 7 * (week - 1) - 1
