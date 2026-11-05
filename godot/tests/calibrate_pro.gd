extends SceneTree
## KBA 프로리그를 여러 시즌 자동으로 돌려 기록 분포를 본다 (튜닝용, 테스트 아님).
##   ~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/calibrate_pro.gd
##
## 리그 경기는 모두 실제 타석(GameRunner)으로 돈다. 한 시즌 144경기 × 10구단 = 720경기라 한 시즌에 몇십 초 걸린다.
## 한국 프로야구 평균(대략): 리그 타율 .260~.275 · OPS .720~.770 · 팀당 경기당 득점 4.5~5.2 · 홈런왕 30~45개 ·
##   타격왕 .340~.370 · 평균자책점 1위 2.3~3.0 · 다승왕 15~20승. 벗어나면 balance.json career.pro.teamStrength 나
##   game.autoPa 를 고친다 (dev-log "프로 편" 항목에 어떤 수치를 바꾸면 무엇이 달라지는지 적어 두었다).

const SEASONS := 2


func _init() -> void:
	var bc := BattingConfig.from_balance(BalanceLoader.load_balance())
	var cfg := CareerConfig.from(BalanceLoader.load_balance(), BalanceLoader.load_config("content.json"))
	var lg := ProLeague.create(cfg, 2026, null)
	print("시즌  리그타율  OPS    경기당득점  홈런왕          타격왕            평균자책1위       다승왕     우승")
	for s in SEASONS:
		for w in range(1, int(cfg.pro["weeks"]) + 1):
			lg.sim_week_others(cfg, bc, w, "")
			lg.weeks_done += 1
		lg.lock_standings()
		while not lg.post_over():
			lg.play_post_round(cfg, bc)
		var total := PlayerData.SeasonStats.new()
		for p: ProPlayer in lg.all_batters():
			total.merge(p.season)
		var runs := 0
		var games := 0
		for t: ProTeam in lg.teams:
			runs += t.runs
			games += t.games()
		var hr := lg.leaders(cfg, "hr", 1)
		var avg := lg.leaders(cfg, "avg", 1)
		var era := lg.leaders(cfg, "era", 1)
		var wins := lg.leaders(cfg, "wins", 1)
		var f := func(rows: Array) -> String:
			return "%s %s" % [(rows[0]["player"] as ProPlayer).name, rows[0]["text"]] if not rows.is_empty() else "-"
		print("%d     %s     %s  %.2f        %-14s %-16s %-16s %-10s %s" % [lg.year, total.average(), total.ops(), float(runs) / games,
			f.call(hr), f.call(avg), f.call(era), f.call(wins), lg.team(lg.champions[-1]["team"]).name])
		var order := []
		for t: ProTeam in lg.standings():
			order.append("%s %d-%d" % [t.short, t.w, t.l])
		print("      순위: " + " · ".join(order))
		lg.give_awards(cfg)
		lg.next_season(cfg)
	quit()
