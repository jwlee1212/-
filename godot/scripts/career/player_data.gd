class_name PlayerData
extends RefCounted
## 내 선수. 능력치 0~99, 컨디션 0~100, 스카우트 관심도 0~100.

const STATS := ["contact", "power", "eye"]
const STAT_LABELS := {"contact": "컨택", "power": "파워", "eye": "선구안"}


## 시즌 누적 기록
class SeasonStats:
	var games := 0
	var pa := 0
	var ab := 0
	var hits := 0
	var doubles := 0
	var triples := 0
	var home_runs := 0
	var rbi := 0
	var walks := 0
	var strikeouts := 0

	func add(outcome: SwingJudge.Outcome, runs_batted_in: int) -> void:
		pa += 1
		rbi += runs_batted_in
		match outcome:
			SwingJudge.Outcome.WALK:
				walks += 1
				return
			SwingJudge.Outcome.SAC_BUNT:
				return  # 희생번트는 타수에 넣지 않는다
			SwingJudge.Outcome.STRIKEOUT:
				strikeouts += 1
			SwingJudge.Outcome.DOUBLE:
				doubles += 1
			SwingJudge.Outcome.TRIPLE:
				triples += 1
			SwingJudge.Outcome.HOME_RUN:
				home_runs += 1
		ab += 1
		if SwingJudge.is_hit(outcome):
			hits += 1

	func average() -> String:
		if ab == 0:
			return "-"
		var avg := roundi(hits * 1000.0 / ab)
		return "1.000" if avg >= 1000 else ".%03d" % avg


var name := "신인"
var grade := 1
var contact := 40
var power := 40
var eye := 40
## 주력 (아직 훈련으로 오르지 않음)
var speed := 50
var condition := 80
var scout_interest := 5
## 타순 (1~9)
var lineup_slot := 7
## 이벤트가 남긴 표시 (나중에 결과가 돌아오는 이벤트용)
var flags := {}
var season := SeasonStats.new()


static func create(player_name: String, cfg: CareerConfig) -> PlayerData:
	var p := PlayerData.new()
	p.name = player_name
	for stat in STATS:
		p.set(stat, int(cfg.start[stat]))
	p.speed = int(cfg.start.get("speed", 50))
	p.condition = int(cfg.start["condition"])
	p.scout_interest = int(cfg.start["scoutInterest"])
	p.lineup_slot = int(cfg.start["lineupSlot"])
	return p


func stat(id: String) -> int:
	return get(id)


func add_stat(id: String, delta: int, cfg: CareerConfig) -> void:
	set(id, clampi(stat(id) + delta, 1, cfg.stat_max))


func add_condition(delta: int) -> void:
	condition = clampi(condition + delta, 0, 100)


func add_scout(delta: int) -> void:
	scout_interest = clampi(scout_interest + delta, 0, 100)


## 경기에서 쓰는 능력치. 컨디션이 좋으면 조금 오르고 나쁘면 내려간다 — 판정 폭으로 손에 느껴진다
func skills_for_game(cfg: CareerConfig) -> BatterSkills:
	var bonus := (condition - cfg.condition_pivot) * cfg.condition_per_point
	return BatterSkills.new(
		clampi(roundi(contact + bonus), 1, 99), clampi(roundi(power + bonus), 1, 99), clampi(roundi(eye + bonus), 1, 99), speed)
