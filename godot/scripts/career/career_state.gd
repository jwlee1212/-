class_name CareerState
extends RefCounted
## 커리어 진행: 주차·시즌, 훈련, 경기 뒤 성장, 돌발 이벤트.
##
## 한 주 = 훈련 고르기(유저 결정 1개) → 경기 → 결과·성장 → 돌발 이벤트 0~1개.
## 모든 난수는 커리어 시드 + 시즌 + 주차로 만든다 — 같은 선택이면 같은 결과다 (원칙 2).

var cfg: CareerConfig
var player: PlayerData
var season_no := 1
var week := 1
## 이번 시즌 경기 결과 (1 승, 0 무, -1 패)
var results: Array[int] = []
## 이미 나온 이벤트 id (once 이벤트용)
var used_events := {}
## 이번 주에 고른 훈련 (경기 결과 화면 설명용)
var last_training := ""


func _init(p_cfg: CareerConfig, player_name: String) -> void:
	cfg = p_cfg
	player = PlayerData.create(player_name, cfg)


func is_season_over() -> bool:
	return week > cfg.season_weeks


func opponent() -> Dictionary:
	return cfg.opponents[(week - 1) % cfg.opponents.size()]


## 홈 경기 여부 (주마다 번갈아)
func is_home() -> bool:
	return week % 2 == 0


func wins() -> int:
	return results.count(1)


func losses() -> int:
	return results.count(-1)


## 훈련. 돌려주는 값: 결과 설명 한 줄
func train(id: String) -> String:
	var t: Dictionary = cfg.training[id]
	last_training = t["label"]
	var parts := []
	var stat: String = t["stat"]
	if stat != "":
		player.add_stat(stat, int(t["gain"]), cfg)
		parts.append("%s +%d" % [PlayerData.STAT_LABELS[stat], int(t["gain"])])
	player.add_condition(int(t["condition"]))
	parts.append("컨디션 %+d" % int(t["condition"]))
	return "%s: %s" % [t["label"], ", ".join(parts)]


func new_game() -> GameRunner:
	return GameRunner.new(cfg, player, opponent(), is_home(), _seed("game"))


## 내 타석 시드 (몇 번째 타석인지로 구분)
func at_bat_seed(game: GameRunner) -> int:
	return _seed("atbat%d" % game.my_line.pa)


## 경기가 끝난 뒤: 기록·성장·스카우트 관심도. 돌려주는 값: 변화 설명 목록
func finish_game(game: GameRunner) -> Array[String]:
	var line := game.my_line
	var g := cfg.growth
	var s := cfg.scout
	var notes: Array[String] = []
	results.append(game.my_result())

	var season := player.season
	season.games += 1
	season.pa += line.pa
	season.ab += line.ab
	season.hits += line.hits
	season.doubles += line.doubles
	season.triples += line.triples
	season.home_runs += line.home_runs
	season.rbi += line.rbi
	season.walks += line.walks
	season.strikeouts += line.strikeouts

	var extra_base := line.doubles + line.triples
	var gains := {
		"contact": line.hits * int(g["perHitContact"]),
		"power": extra_base * int(g["perExtraBasePower"]) + line.home_runs * int(g["perHomeRunPower"]),
		"eye": line.walks * int(g["perWalkEye"]),
	}
	for stat: String in gains:
		if gains[stat] > 0:
			player.add_stat(stat, gains[stat], cfg)
			notes.append("%s +%d" % [PlayerData.STAT_LABELS[stat], gains[stat]])
	player.add_condition(int(g["conditionPerGame"]))

	var scout_delta := line.hits * int(s["perHit"]) + line.home_runs * int(s["perHomeRun"]) \
		+ line.strikeouts * int(s["perStrikeout"]) + (int(s["perWin"]) if game.my_result() > 0 else 0)
	if scout_delta != 0:
		player.add_scout(scout_delta)
		notes.append("스카우트 관심 %+d" % scout_delta)
	return notes


## 이번 주 돌발 이벤트 (없으면 빈 Dictionary)
func pick_event() -> Dictionary:
	var rng := _rng("event")
	if rng.randf() >= cfg.event_chance:
		return {}
	var eligible := []
	for e: Dictionary in cfg.events:
		if _eligible(e):
			eligible.append(e)
	if eligible.is_empty():
		return {}
	return eligible[rng.randi_range(0, eligible.size() - 1)]


## 이벤트 선택 결과 적용. 돌려주는 값: 결과 문장 + 변화 설명
func resolve_event(event: Dictionary, choice_index: int) -> String:
	used_events[event["id"]] = true
	var choice: Dictionary = event["choices"][choice_index]
	var fx: Dictionary = choice.get("effects", {})
	var parts := []
	for key: String in fx:
		var v: Variant = fx[key]
		match key:
			"condition":
				player.add_condition(int(v))
				parts.append("컨디션 %+d" % int(v))
			"scoutInterest":
				player.add_scout(int(v))
				parts.append("스카우트 관심 %+d" % int(v))
			"lineupSlot":
				player.lineup_slot = int(v)
				parts.append("타순 %d번" % int(v))
			"flag":
				player.flags[str(v)] = true
			_:
				if key in PlayerData.STATS:
					player.add_stat(key, int(v), cfg)
					parts.append("%s %+d" % [PlayerData.STAT_LABELS[key], int(v)])
				else:
					assert(false, "content.json: 알 수 없는 이벤트 효과 %s" % key)
	var summary: String = choice["result"]
	return summary if parts.is_empty() else "%s (%s)" % [summary, ", ".join(parts)]


## 이벤트가 없는 주의 감독 한마디 (결과와 내 활약으로 고른다)
func coach_line(game: GameRunner) -> String:
	var result_key: String = ["loss", "draw", "win"][game.my_result() + 1]
	var form := "hot" if game.my_line.hits >= 2 else "cold"
	var lines: Array = cfg.coach_lines[result_key + "_" + form]
	return lines[_rng("coach").randi_range(0, lines.size() - 1)]


func advance_week() -> void:
	week += 1


## 다음 시즌 (학년이 오르고 기록은 새로)
func start_next_season() -> void:
	season_no += 1
	player.grade = mini(player.grade + 1, 3)
	player.season = PlayerData.SeasonStats.new()
	results.clear()
	week = 1


func _eligible(e: Dictionary) -> bool:
	if e.get("once", false) and used_events.has(e["id"]):
		return false
	var span: Array = e.get("week", [1, cfg.season_weeks])
	if week < int(span[0]) or week > int(span[1]):
		return false
	for f: String in e.get("requires", []):
		if not player.flags.has(f):
			return false
	return player.scout_interest >= int(e.get("minScout", 0))


func _rng(salt: String) -> RandomNumberGenerator:
	var r := RandomNumberGenerator.new()
	r.seed = _seed(salt)
	return r


func _seed(salt: String) -> int:
	return hash([cfg.seed, season_no, week, salt])
