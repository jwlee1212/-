class_name BoxScore
extends RefCounted
## 박스스코어: 타자별·투수별 기록. 경기 무결성 검사(점수 = 득점 합 = 실점 합 등)의 근거가 된다.


class BatterLine:
	var pa := 0
	var ab := 0
	var h := 0
	var doubles := 0
	var triples := 0
	var hr := 0
	var rbi := 0
	var r := 0
	var bb := 0
	var so := 0
	var sh := 0
	var sf := 0

	func add(outcome: SwingJudge.Outcome, rbi_count: int, sac_fly: bool) -> void:
		pa += 1
		rbi += rbi_count
		match outcome:
			SwingJudge.Outcome.WALK:
				bb += 1
				return
			SwingJudge.Outcome.SAC_BUNT:
				sh += 1
				return
		if sac_fly:
			sf += 1
			return
		ab += 1
		match outcome:
			SwingJudge.Outcome.STRIKEOUT: so += 1
			SwingJudge.Outcome.DOUBLE: doubles += 1
			SwingJudge.Outcome.TRIPLE: triples += 1
			SwingJudge.Outcome.HOME_RUN: hr += 1
		if SwingJudge.is_hit(outcome):
			h += 1


class PitcherLine:
	var bf := 0
	var outs := 0
	var h := 0
	var r := 0
	var bb := 0
	var so := 0
	var hr := 0
	var pitches := 0

	func innings_text() -> String:
		return "%d%s" % [outs / 3, ["", " ⅓", " ⅔"][outs % 3]]


## [팀][타순] / [팀][투수 번호]
var batters := [[], []]
var pitchers := [[], []]
## 팀별 안타 (점수판 H)
var hits := [0, 0]


func _init(away_pitchers: int, home_pitchers: int) -> void:
	for side in 2:
		for i in 9:
			batters[side].append(BatterLine.new())
	for i in away_pitchers:
		pitchers[0].append(PitcherLine.new())
	for i in home_pitchers:
		pitchers[1].append(PitcherLine.new())


func team_runs(side: int) -> int:
	var total := 0
	for b: BatterLine in batters[side]:
		total += b.r
	return total


func runs_allowed(side: int) -> int:
	var total := 0
	for p: PitcherLine in pitchers[side]:
		total += p.r
	return total
