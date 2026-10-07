extends SceneTree
## 고교 3년 자동 커리어를 여러 번 돌려 스카우트 평가·이벤트 수 분포를 본다 (튜닝용, 테스트 아님).
##   ~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/calibrate_career.gd
##
## 자동 선수: 컨디션이 50 아래면 휴식, 아니면 네 훈련을 돌아가며. 내 타석은 AutoPa(다른 타자와 같은 자동 타석).
## 이벤트 선택지는 커리어마다 다른 시드로 무작위.
## 목표: 평범한 잠재력이면 3학년 끝에 2~5라운드, 잘 크면 1라운드, 못 크면 하위 라운드·지명 밖.

const CAREERS := 16


func _init() -> void:
	var bc := BattingConfig.from_balance(BalanceLoader.load_balance())
	print("시드  종합(잠재) 랭킹 1→2→3학년   예상 지명            라이벌(종합·랭킹)  관심 1→2→3학년  평판 신뢰  이벤트  진로  | 번 돈  남은 돈  장비  연애")
	var ranks := []
	for i in CAREERS:
		var cfg := CareerConfig.from(BalanceLoader.load_balance(), BalanceLoader.load_config("content.json"))
		cfg.seed = 1000 + i
		var pick := RandomNumberGenerator.new()
		pick.seed = 77 + i
		var career := CareerState.new(cfg, "자동", bc)
		var earned := 0
		var season_ranks := []
		var season_scout := []
		var ids := ["contact", "power", "eye", "speed"]
		for season in 3:
			var weeks := 0
			while not career.is_season_over():
				for e: Dictionary in career.events("start").duplicate():
					career.resolve_event(e, _ok(career, e, pick.randi_range(0, (e.choices as Array).size() - 1)))
				career.train("rest" if career.player.condition < 50 else ids[weeks % ids.size()])
				# 홀수 시드: 처음 만난 연애 후보와 돈이 되면 매주 만난다 / 짝수 시드: 돈을 모아 장비·레슨
				if i % 2 == 1:
					for id: String in ["manager", "neighbor", "reporter"]:
						if career.social_block(id, "hangout") == "" and career.player.money >= 30000:
							career.socialize(id, "hangout")
							break
				else:
					for g: String in ["gloves", "maple_bat", "spikes", "goggles", "pro_bat"]:
						if career.buy_gear(g) == "":
							break
					if career.player.money >= 150000:
						career.use_service("lesson")
				var game := career.new_game()
				var rng := RandomNumberGenerator.new()
				rng.seed = hash([cfg.seed, season, weeks])
				while not game.state.over:
					if game.step().type == "my_turn":
						var pa := AutoPa.simulate(cfg.auto_pa, bc, game.current_batter().skills, game.current_pitcher(), rng)
						game.apply_my_result(AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, null))
				career.finish_game(game)
				for pair: Array in career.last_income:
					earned += int(pair[1])
				for e: Dictionary in career.events("after").duplicate():
					career.resolve_event(e, _ok(career, e, pick.randi_range(0, (e.choices as Array).size() - 1)))
				career.advance_week()
				weeks += 1
			season_ranks.append(career.prospect_rank())
			season_scout.append(career.player.scout_interest)
			for e: Dictionary in career.events("seasonEnd").duplicate():
				career.resolve_event(e, _ok(career, e, pick.randi_range(0, (e.choices as Array).size() - 1)))
			if season < 2:
				career.winter_training()
				career.start_next_season()
		var p := career.player
		ranks.append(career.prospect_rank())
		print("%d  %d(%d)     %-14s %-20s %d·%d위           %-14s %d   %d    %d     %s" % [cfg.seed, p.overall(), roundi(p.potential_average()),
			"→".join(season_ranks.map(func(r: int) -> String: return str(r))), career.projection()["text"],
			career.rival.player.overall(), career.rival_rank(), "→".join(season_scout.map(func(r: int) -> String: return str(r))), p.reputation, career.people.relation("coach"),
			career.book.log.size(), career.path])
		var love := []
		for entry: Dictionary in career.book.log:
			if str(entry["title"]).begins_with("고백") or entry["title"] == "위기":
				love.append("%s(%d학년 %d주: %s)" % [entry["title"], entry["grade"], entry["week"], entry["choice"]])
		print("      | 번 돈 %s · 남은 돈 %s · 장비 %s · 연애 %s %s" % [PlayerData.money_text(earned), PlayerData.money_text(p.money), p.gear.keys(),
			career.people.partner if career.people.partner != "" else "-", " ".join(love)])
	ranks.sort()
	print("3학년 끝 랭킹 중앙값 %d위 (최고 %d, 최저 %d)" % [ranks[ranks.size() / 2], ranks[0], ranks[-1]])
	quit()


## 고를 수 있는 선택지 (돈이 모자라면 다른 것)
func _ok(career: CareerState, e: Dictionary, want: int) -> int:
	if EventBook.choice_block(career, e.choices[want]) == "":
		return want
	for i in (e.choices as Array).size():
		if EventBook.choice_block(career, e.choices[i]) == "":
			return i
	return 0
