class_name ProNational
extends RefCounted
## 국가대표 (docs/worldview.md 5-6, balance.json career.pro.national): 세계야구클래식 · 올림픽 · 아시안게임.
## 대회마다 4년 주기, 정해진 주의 정규시즌을 마친 뒤 명단을 뽑는다 — 리그 타자 기여도(ProPlayer.value) 순위로.
## 뽑히면 대회를 자동 타석으로 치르고(내 기록은 국가대표 기록으로 따로), 결과(메달)를 시드 난수로 정한다.
## 아시안게임 금·올림픽 메달이면 병역 면제 (ProService.exempt). 현역 복무 중이면 못 나간다 (상무는 나간다).

## 이번 주(정규시즌 weeks_done 주를 막 마친 뒤)에 열리는 대회들
static func events_now(cfg: CareerConfig, pro: ProCareer) -> Array:
	var out := []
	for ev: Dictionary in cfg.pro["national"]["events"]:
		if pro.league.year % int(ev["every"]) == int(ev["offset"]) and pro.league.weeks_done == int(ev["week"]):
			out.append(ev)
	return out


## 대표 선발 순위: 리그 타자(+ 나) 기여도 순위. last: 지난 시즌 기여도(세계야구클래식)
static func my_rank(pro: ProCareer, last: bool) -> int:
	if last:
		return pro.last_value_rank
	var lops := pro.league.league_ops()
	var lra := pro.league.league_ra9()
	var mine := pro.me.value(lops, lra)
	if pro.me.season.pa == 0 and pro.military != ProService.SANGMU:
		return 999
	var rank := 1
	for p: ProPlayer in pro.league.all_batters():
		if p != pro.me and p.value(lops, lra) > mine:
			rank += 1
	return rank


static func selected(cfg: CareerConfig, pro: ProCareer, ev: Dictionary) -> bool:
	if pro.military == ProService.ACTIVE:
		return false
	var rank := my_rank(pro, bool(ev.get("useLastSeason", false)))
	if rank <= 0:
		return false
	if rank <= int(ev["pickTop"]):
		return true
	return pro.me.age <= int(ev["youngAge"]) and rank <= int(ev["youngPickTop"])


## 이번 주 대회를 치른다 (ProCareer.finish_week). 돌려주는 값: 치른 대회 결과들
static func week(career: CareerState, pro: ProCareer, notes: Array[String]) -> Array:
	var cfg := career.cfg
	var nc: Dictionary = cfg.pro["national"]
	var out := []
	for ev: Dictionary in events_now(cfg, pro):
		var key := "%s:%d" % [ev["id"], pro.league.year]
		if pro.national_done.has(key):
			continue
		pro.national_done[key] = true
		if not selected(cfg, pro, ev):
			notes.append("%s 대표팀 명단에 들지 못했다" % ev["name"])
			continue
		var res := play(career, pro, ev)
		out.append(res)
		pro.national.append(res)
		pro.fame = mini(100, pro.fame + int(nc["famePerPick"]))
		notes.append("%s 국가대표! %s — %s" % [ev["name"], res["result"], (res["line"] as PlayerData.SeasonStats).line_text()])
		pro.league.week_events.append({"kind": "national", "title": "%s %s" % [ev["name"], res["result"]]})
		career.player.flags["national_" + str(ev["id"])] = true
		Milestones.fire_event(career, pro, "national")
		if res["result"] in ev["exempt"]:
			Milestones.fire_event(career, pro, "medal_" + str(ev["id"]))
			ProService.exempt(career, pro, notes)
		# 대회 경험: 국가대표 기록으로도 자란다
		Growth.from_line(cfg, career.player, res["line"])
	return out


## 대회 하나: games 경기 × paPerGame 자동 타석 (상대 투수 전력 opponentStrength) → 내 줄, 결과는 시드 난수
static func play(career: CareerState, pro: ProCareer, ev: Dictionary) -> Dictionary:
	var cfg := career.cfg
	var rng := RandomNumberGenerator.new()
	rng.seed = hash([pro.league.seed, pro.league.year, ev["id"], "national"])
	var line := PlayerData.SeasonStats.new()
	var skills := career.player.skills_for_game(cfg)
	var sd := float(cfg.roster["ratingSd"])
	for g in int(ev["games"]):
		var pitcher := Team.Pitcher.new()
		pitcher.stuff = Team._rating(float(ev["opponentStrength"]), sd, rng)
		pitcher.control = Team._rating(float(ev["opponentStrength"]), sd, rng)
		line.games += 1
		for i in int(ev["paPerGame"]):
			var r := AutoPa.simulate(cfg.auto_pa, career.batting, skills, pitcher, rng)
			var o: SwingJudge.Outcome = r["outcome"]
			var rbi := 1 if o == SwingJudge.Outcome.HOME_RUN or (SwingJudge.is_hit(o) and rng.randf() < 0.3) else 0
			line.add(o, rbi)
	var bonus := float(cfg.pro["national"]["hotBonus"]) if line.ab > 0 and line.obp() + line.slg() >= 0.9 else 0.0
	var roll := maxf(0.0, rng.randf() - bonus)
	var result: String = ev["medals"][-1][0]
	for m: Array in ev["medals"]:
		if roll < float(m[1]):
			result = m[0]
			break
	return {"id": ev["id"], "name": ev["name"], "year": pro.league.year, "result": result, "line": line}
