class_name GameState
extends RefCounted
## 한 경기의 상태: 이닝·초말·아웃·주자·점수. 타석 결과를 받아 주루와 득점을 처리한다.
##
## 주루는 근사다 (수비수가 없으므로): 안타는 정해진 만큼 진루하고, 몇몇 상황만 확률로 정한다
## (단타 때 2루 주자 홈인, 2루타 때 1루 주자 홈인, 땅볼 병살, 뜬공 희생플라이). 확률은 balance.json game.running.

const AWAY := 0
const HOME := 1

var innings: int
var inning := 1
var top := true
var outs := 0
var bases := [false, false, false]  # 1루, 2루, 3루
var score := [0, 0]
## 팀별 다음 타자 (0~8)
var batter_index := [0, 0]
var over := false
## 이닝별 점수 (점수판용) [팀][이닝-1]
var line := [[], []]

var _running: Dictionary


func _init(p_innings: int, running: Dictionary) -> void:
	innings = p_innings
	_running = running
	line = [[0], []]


func batting_side() -> int:
	return AWAY if top else HOME


func runners_text() -> String:
	var names := []
	for i in 3:
		if bases[i]:
			names.append("%d" % (i + 1))
	if names.is_empty():
		return "주자 없음"
	if names.size() == 3:
		return "만루"
	return "·".join(names) + "루"


func half_text() -> String:
	return "%d회%s" % [inning, "초" if top else "말"]


## 타석 결과 적용. bunt: 번트 타구였는가 (주자가 있고 무사·1사인 땅볼 아웃이면 희생번트가 된다)
## 돌려주는 값: {"outcome": 실제 기록될 결과, "runs": 득점, "rbi": 타점, "note": 덧붙일 설명, "half_over": 이닝 교대 여부}
func apply(outcome: SwingJudge.Outcome, rng: RandomNumberGenerator, bunt: bool = false) -> Dictionary:
	var side := batting_side()
	var runs := 0
	var note := ""
	var outs_before := outs
	var has_runner: bool = bases[0] or bases[1] or bases[2]
	if bunt and outcome == SwingJudge.Outcome.GROUND_OUT and has_runner and outs_before < 2:
		outcome = SwingJudge.Outcome.SAC_BUNT
	match outcome:
		SwingJudge.Outcome.SAC_BUNT:
			# 타자는 아웃, 주자는 한 베이스씩 (3루 주자는 홈인 = 스퀴즈)
			outs += 1
			note = "희생번트"
			if outs < 3:
				if bases[2]:
					runs += 1
				bases = [false, bases[0], bases[1]]
		SwingJudge.Outcome.STRIKEOUT, SwingJudge.Outcome.LINE_OUT:
			outs += 1
		SwingJudge.Outcome.FLY_OUT:
			outs += 1
			if bases[2] and outs_before < 2 and rng.randf() < float(_running["sacFlyChance"]):
				bases[2] = false
				runs += 1
				note = "희생플라이"
		SwingJudge.Outcome.GROUND_OUT:
			if bases[0] and outs_before < 2 and rng.randf() < float(_running["doublePlayChance"]):
				outs += 2
				bases[0] = false
				note = "병살"
			else:
				outs += 1
			if outs < 3:
				# 진루타: 주자가 한 베이스씩 (3루 주자는 무사·1사일 때만 홈인)
				if bases[2] and outs_before < 2:
					runs += 1
				bases = [false, bases[0], bases[1] or (bases[2] and outs_before >= 2)]
		SwingJudge.Outcome.WALK:
			if bases[0]:
				if bases[1]:
					if bases[2]:
						runs += 1
					bases[2] = true
				bases[1] = true
			bases[0] = true
		SwingJudge.Outcome.SINGLE:
			if bases[2]:
				runs += 1
			var second_scores: bool = bases[1] and rng.randf() < float(_running["singleScoresFromSecond"])
			if second_scores:
				runs += 1
			bases = [true, bases[0], bases[1] and not second_scores]
		SwingJudge.Outcome.DOUBLE:
			runs += int(bases[2]) + int(bases[1])
			var first_scores: bool = bases[0] and rng.randf() < float(_running["doubleScoresFromFirst"])
			if first_scores:
				runs += 1
			bases = [false, true, bases[0] and not first_scores]
		SwingJudge.Outcome.TRIPLE:
			runs += int(bases[0]) + int(bases[1]) + int(bases[2])
			bases = [false, false, true]
		SwingJudge.Outcome.HOME_RUN:
			runs += int(bases[0]) + int(bases[1]) + int(bases[2]) + 1
			bases = [false, false, false]
	_add_runs(side, runs)
	batter_index[side] = (batter_index[side] + 1) % 9
	var half_over := false
	if outs >= 3:
		half_over = true
		_end_half()
	elif not top and inning >= innings and score[HOME] > score[AWAY]:
		over = true  # 끝내기
	return {"outcome": outcome, "runs": runs, "rbi": runs, "note": note, "half_over": half_over}


func _add_runs(side: int, runs: int) -> void:
	score[side] += runs
	line[side][inning - 1] += runs


func _end_half() -> void:
	outs = 0
	bases = [false, false, false]
	if top:
		# 마지막 이닝 초, 홈 팀이 이기고 있으면 말 공격 없이 끝
		if inning >= innings and score[HOME] > score[AWAY]:
			over = true
			return
		top = false
		line[HOME].append(0)
	else:
		if inning >= innings:
			over = true  # 정규 이닝 종료 (동점이면 무승부 — 프로토타입)
			return
		inning += 1
		top = true
		line[AWAY].append(0)
