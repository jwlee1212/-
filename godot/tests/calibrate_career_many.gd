extends SceneTree
## 고교 3년 자동 커리어를 많이 돌려 커리어마다 한 줄(JSON)로 뽑는다 (밸런스 보고서용 도구, 테스트 아님).
## calibrate_career.gd 와 같은 자동 선수를 쓰고, 표는 따로 분석한다 (docs/reports/).
##   CAREERS=240 START=0 Godot --headless --path godot --script res://tests/calibrate_career_many.gd > out.txt
##   (줄 앞에 "CAREER " 이 붙은 줄만 JSON. 여러 프로세스로 나눠 돌릴 때는 START 를 다르게 준다)
##
## 자동 선수 (calibrate_career.gd 와 같다):
## - 컨디션이 50 아래면 휴식, 아니면 컨택·파워·선구안·주력 훈련을 돌아가며
## - 홀수 번 커리어: 처음 만난 연애 후보와 돈이 되면 매주 만난다 / 짝수 번: 돈을 모아 장비·레슨
## - 내 타석은 AutoPa (다른 타자와 같은 자동 타석) — 유저가 직접 칠 때와 다를 수 있다
## - 이벤트 선택지는 커리어마다 다른 시드로 무작위 (돈이 모자라면 고를 수 있는 것)

const SEED_BASE := 1000


func _init() -> void:
	var n := int(OS.get_environment("CAREERS")) if OS.get_environment("CAREERS") != "" else 16
	var start := int(OS.get_environment("START")) if OS.get_environment("START") != "" else 0
	var balance := BalanceLoader.load_balance()
	var bc := BattingConfig.from_balance(balance)
	for i in range(start, start + n):
		print("CAREER " + JSON.stringify(_career(i, bc)))
	quit()


func _career(i: int, bc: BattingConfig) -> Dictionary:
	var cfg := CareerConfig.from(BalanceLoader.load_balance(), BalanceLoader.load_config("content.json"))
	cfg.seed = SEED_BASE + i
	var pick := RandomNumberGenerator.new()
	pick.seed = 77 + i
	var career := CareerState.new(cfg, "자동", bc)
	var p := career.player
	var out := {
		"i": i, "seed": cfg.seed, "strategy": "dating" if i % 2 == 1 else "gear",
		"potential": p.potential.duplicate(), "start": _stats(p),
		"seasons": [], "money": {"start": p.money, "income": 0, "income_by": {}, "gear": 0, "service": 0, "social": 0, "event_cost": 0, "event_gain": 0},
		"events_per_week": [], "decisions_per_week": [], "injury_starts": 0, "injured_weeks": 0, "lessons": 0,
	}
	var ids := ["contact", "power", "eye", "speed"]
	var money: Dictionary = out["money"]
	for season in 3:
		var weeks := 0
		var injured_before := false
		while not career.is_season_over():
			var ev := 0
			var m0 := p.money
			for e: Dictionary in career.events("start").duplicate():
				career.resolve_event(e, _ok(career, e, pick.randi_range(0, (e.choices as Array).size() - 1)))
				ev += 1
			_event_money(money, p.money - m0)
			career.train("rest" if p.condition < 50 else ids[weeks % ids.size()])
			if i % 2 == 1:
				for id: String in ["manager", "neighbor", "reporter"]:
					if career.social_block(id, "hangout") == "" and p.money >= 30000:
						var b := p.money
						career.socialize(id, "hangout")
						money["social"] += b - p.money
						break
			else:
				for g: String in ["gloves", "maple_bat", "spikes", "goggles", "pro_bat"]:
					var b := p.money
					if career.buy_gear(g) == "":
						money["gear"] += b - p.money
						break
				if p.money >= 150000:
					var b := p.money
					if not career.use_service("lesson").begins_with("!"):
						money["service"] += b - p.money
						out["lessons"] += 1
			var game := career.new_game()
			var rng := RandomNumberGenerator.new()
			rng.seed = hash([cfg.seed, season, weeks])
			while not game.state.over:
				if game.step().type == "my_turn":
					var pa := AutoPa.simulate(cfg.auto_pa, bc, game.current_batter().skills, game.current_pitcher(), rng)
					game.apply_my_result(AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, null))
			career.finish_game(game)
			for pair: Array in career.last_income:
				money["income"] += int(pair[1])
				money["income_by"][pair[0]] = int(money["income_by"].get(pair[0], 0)) + int(pair[1])
			if p.is_injured():
				out["injured_weeks"] += 1
				if not injured_before:
					out["injury_starts"] += 1
			injured_before = p.is_injured()
			m0 = p.money
			for e: Dictionary in career.events("after").duplicate():
				career.resolve_event(e, _ok(career, e, pick.randi_range(0, (e.choices as Array).size() - 1)))
				ev += 1
			_event_money(money, p.money - m0)
			out["events_per_week"].append(ev)
			out["decisions_per_week"].append(ev + 1)  # 훈련 1 + 이벤트 (상점·교류는 선택)
			career.advance_week()
			weeks += 1
		var s := p.season
		var r := career.rival.player.season
		out["seasons"].append({
			"grade": p.grade, "weeks": weeks, "stats": _stats(p), "overall": p.overall(),
			"rank": career.prospect_rank(), "projection": career.projection()["text"], "pick_range": _range(cfg, career.prospect_rank()),
			"scout": p.scout_interest, "reputation": p.reputation, "coach": career.people.relation("coach"), "form": p.form,
			"line": _line(s), "team": [career.wins(), career.losses(), career.results.count(0)], "phases": career.phase_log.duplicate(),
			"rival": {"overall": career.rival.player.overall(), "rank": career.rival_rank(), "line": _line(r), "stats": _stats(career.rival.player)},
			"money": p.money, "events": career.book.log.size(),
		})
		var m1 := p.money
		for e: Dictionary in career.events("seasonEnd").duplicate():
			career.resolve_event(e, _ok(career, e, pick.randi_range(0, (e.choices as Array).size() - 1)))
		_event_money(money, p.money - m1)
		if season < 2:
			career.winter_training()
			career.start_next_season()
	out["final_money"] = p.money
	out["gear"] = p.gear.keys()
	out["partner"] = career.people.partner
	out["path"] = career.path
	out["log"] = career.book.log.map(func(e: Dictionary) -> Array: return [e["grade"], e["week"], e["title"], e["choice"], e["story"]])
	out["flags"] = p.flags.keys()
	out["final_potential_avg"] = p.potential_average()
	return out


func _event_money(money: Dictionary, delta: int) -> void:
	if delta < 0:
		money["event_cost"] += -delta
	elif delta > 0:
		money["event_gain"] += delta


func _stats(p: PlayerData) -> Dictionary:
	return {"contact": p.contact, "power": p.power, "eye": p.eye, "speed": p.speed}


func _line(s: PlayerData.SeasonStats) -> Dictionary:
	return {"g": s.games, "pa": s.pa, "ab": s.ab, "h": s.hits, "d": s.doubles, "t": s.triples, "hr": s.home_runs,
		"rbi": s.rbi, "bb": s.walks, "so": s.strikeouts}


## 고를 수 있는 선택지 (돈이 모자라면 다른 것)
func _ok(career: CareerState, e: Dictionary, want: int) -> int:
	if EventBook.choice_block(career, e.choices[want]) == "":
		return want
	for i in (e.choices as Array).size():
		if EventBook.choice_block(career, e.choices[i]) == "":
			return i
	return 0


func _range(cfg: CareerConfig, rank: int) -> Array:
	var r := Scouting.pick_range(cfg, rank)
	return [r.x, r.y]
