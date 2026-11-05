class_name ProPlayer
extends RefCounted
## KBA 프로리그 선수 한 명 (타자 또는 투수). 내 선수·강태오도 이 객체로 리그 명단에 들어간다.
## 능력치는 경기에 쓰는 Team.Batter / Team.Pitcher 를 그대로 들고 있고, 기록은 경기 박스스코어에서만 쌓인다
## (그래서 순위표·개인 기록·팀 득점이 언제나 서로 맞는다 — 기록을 따로 지어내지 않는다).


## 투수 시즌 기록 (박스스코어 PitcherLine 을 더한 것). 자책점은 따로 세지 않아서 평균자책점은 실점으로 낸다
class PitchStats:
	var games := 0
	var starts := 0
	var outs := 0
	var h := 0
	var r := 0
	var bb := 0
	var so := 0
	var hr := 0
	var wins := 0
	var losses := 0

	func add_line(pl: BoxScore.PitcherLine, started: bool) -> void:
		games += 1
		if started:
			starts += 1
		outs += pl.outs
		h += pl.h
		r += pl.r
		bb += pl.bb
		so += pl.so
		hr += pl.hr

	func merge(o: PitchStats) -> void:
		games += o.games
		starts += o.starts
		outs += o.outs
		h += o.h
		r += o.r
		bb += o.bb
		so += o.so
		hr += o.hr
		wins += o.wins
		losses += o.losses

	## 평균자책점 (9이닝당 실점). 던진 이닝이 없으면 -1
	func era() -> float:
		return r * 27.0 / outs if outs > 0 else -1.0

	func era_text() -> String:
		return "%.2f" % era() if outs > 0 else "-"

	func innings_text() -> String:
		return "%d%s" % [outs / 3, ["", " ⅓", " ⅔"][outs % 3]]


## "dr:b4" 처럼 구단 id + 자리. 같은 리그 안에서 겹치지 않는다
var id := ""
var name := ""
var team_id := ""
## 타자 포지션 ("유격수" …) 또는 "선발"·"불펜"
var position := ""
var is_pitcher := false
var age := 25
## 프로에 들어온 시즌 (ProLeague.year). 신인왕 후보 = 올해 들어온 선수
var rookie_year := 0
var is_me := false
var is_rival := false
## 경기에 쓰는 능력치 (타자면 batter, 투수면 arm). 리그 경기가 이 객체를 그대로 명단에 넣는다
var batter: Team.Batter
var arm: Team.Pitcher
## 이번 시즌 · 포스트시즌 · 한국시리즈 · 통산 기록 (타자)
var season := PlayerData.SeasonStats.new()
var post := PlayerData.SeasonStats.new()
var ks := PlayerData.SeasonStats.new()
var career := PlayerData.SeasonStats.new()
## 투수 기록
var pitching := PitchStats.new()
var pitching_post := PitchStats.new()
var pitching_career := PitchStats.new()
## 지난 시즌들 [{"year", "team", "age", "line": "타율 .301 · 12홈런 …"}]
var history: Array = []


static func make_batter(p_id: String, p_name: String, team: String, position_: String, skills: BatterSkills, p_age: int, year: int) -> ProPlayer:
	var p := ProPlayer.new()
	p.id = p_id
	p.name = p_name
	p.team_id = team
	p.position = position_
	p.age = p_age
	p.rookie_year = year
	p.batter = Team.Batter.new()
	p.batter.name = p_name
	p.batter.position = position_
	p.batter.skills = skills
	return p


static func make_pitcher(p_id: String, p_name: String, team: String, role: String, stuff: int, control: int, profile: String,
		max_pitches: int, p_age: int, year: int) -> ProPlayer:
	var p := ProPlayer.new()
	p.id = p_id
	p.name = p_name
	p.team_id = team
	p.position = role
	p.is_pitcher = true
	p.age = p_age
	p.rookie_year = year
	p.arm = Team.Pitcher.new()
	p.arm.name = p_name
	p.arm.stuff = stuff
	p.arm.control = control
	p.arm.profile = profile
	p.arm.max_pitches = max_pitches
	return p


## 능력치 한 줄 평가 (순위·라인업 정렬용, 0~100 근처)
func rating() -> float:
	if is_pitcher:
		return (arm.stuff + arm.control) / 2.0
	var s := batter.skills
	return (s.contact + s.power + s.eye) / 3.0 * 0.85 + s.speed * 0.15


## 기여도 (MVP·골든글러브·신인왕 점수): 리그 평균보다 얼마나 잘했나 × 얼마나 많이 뛰었나.
## 타자: (OPS − 리그 OPS) × 타석 / 25 + 홈런 × 0.1 + 도루 × 0.05, 투수: (리그 실점률 − 내 실점률) × 이닝 / 9 / 2.2 + 승 × 0.1
func value(league_ops: float, league_ra9: float) -> float:
	if is_pitcher:
		if pitching.outs == 0:
			return 0.0
		return (league_ra9 - pitching.era()) * (pitching.outs / 27.0) / 2.2 + pitching.wins * 0.1
	if season.pa == 0:
		return 0.0
	return (season.obp() + season.slg() - league_ops) * season.pa / 25.0 + season.home_runs * 0.1 + season.steals * 0.05


## 화면용 시즌 한 줄
func line_text() -> String:
	if is_pitcher:
		return "%d승 %d패 · 평균자책 %s · %s이닝 %d삼진" % [pitching.wins, pitching.losses, pitching.era_text(), pitching.innings_text(), pitching.so]
	var s := season
	return "타율 %s · %d홈런 %d타점 · OPS %s (%d경기)" % [s.average(), s.home_runs, s.rbi, s.ops(), s.games]


## 시즌을 닫는다: 통산에 더하고 지난 시즌 한 줄을 남긴 뒤 새 기록으로
func close_season(year: int, team_name: String) -> void:
	history.append({"year": year, "team": team_name, "age": age, "line": line_text(),
		"hr": season.home_runs, "hits": season.hits, "avg": season.average(), "wins": pitching.wins})
	career.merge(season)
	pitching_career.merge(pitching)
	season = PlayerData.SeasonStats.new()
	post = PlayerData.SeasonStats.new()
	ks = PlayerData.SeasonStats.new()
	pitching = PitchStats.new()
	pitching_post = PitchStats.new()
