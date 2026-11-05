class_name ProLeague
extends RefCounted
## KBA 프로리그 (docs/worldview.md 5장): 10구단, 팀당 144경기 = weeks 주 × 한 주 gamesPerWeek 경기(3연전 두 개).
## 리그의 모든 경기는 GameRunner 로 실제로 한 타석씩 돌린다 — 선수마다 능력치(ProPlayer)가 있고, 기록은 그 경기들의
## 박스스코어를 더한 것뿐이다. 그래서 팀 득점 = 선수 득점 합, 순위표 승패 = 경기 결과, 홈런왕 = 실제로 친 홈런 수.
## 포스트시즌: 5위까지 — 와일드카드(4위 1승 안고 시작) → 준플레이오프 → 플레이오프 → 한국시리즈.
## 난수는 리그 시드 + 연도 + 주차 + 경기 번호로만 만든다 (원칙 2).

const POST_ROUNDS := ["wildcard", "semi", "playoff", "ks"]

var seed := 0
## 몇 번째 시즌인가 (주인공 입단 해 = 1)
var year := 1
var teams: Array = []   # ProTeam, content 순서
var by_id := {}
## 정규시즌 몇 주차를 마쳤나 (0~weeks)
var weeks_done := 0
## 포스트시즌: 지금 몇 번째 라운드 (0~3, 4 = 끝) · 라운드별 결과 [{"round", "hi", "lo", "wins": [hi, lo], "winner", "games"}]
var post_round := 0
var post_results: Array = []
## 지금 포스트시즌 라운드 진행 (post_begin — {} 이면 라운드 사이)
var post_state := {}
## 정규시즌 최종 순위 (팀 id, 1위부터 — 시즌이 끝나면 채운다)
var final_order: Array = []
## 역대 우승 [{"year", "team", "runner"}] · 역대 시상 [{"year", "award", "name", "team", "line"}]
var champions: Array = []
var awards: Array = []
## 이번 주에 만든 사건 (뉴스·SNS 재료): [{"kind", ...}]
var week_events: Array = []
## 은퇴·방출한 선수 기록 (역대 순위): [{"id", "name", "team", "first", "last", "pitcher", "games", "hits", "hr", "rbi", "sb", "avg",
##   "wins", "so", "why", "me", "rival"}]
var retired: Array = []
var _pid := 0


## cfg.pro (balance.json career.pro) 와 content.json proTeams 로 리그를 만든다. 강태오(rival)는 대륙 웨일즈 유격수로 들어간다
static func create(cfg: CareerConfig, p_seed: int, rival: PlayerData) -> ProLeague:
	var lg := ProLeague.new()
	lg.seed = p_seed
	var pc := cfg.pro
	var rng := lg._rng("create")
	for d: Dictionary in cfg.pro_teams:
		var t := ProTeam.new()
		t.id = d["id"]
		t.name = d["name"]
		t.short = d["short"]
		t.city = d.get("city", "")
		t.tier = d.get("tier", "중")
		t.style = d.get("style", "")
		t.strength = float(pc["teamStrength"][t.tier]) + rng.randfn(0.0, float(pc["strengthJitter"]))
		t.school = School.create("pro_" + t.id, t.name, t.short, t.strength, Team.PITCHER_PROFILES[rng.randi_range(0, 2)], cfg, rng)
		t.futures = School.create("fut_" + t.id, t.name + " 퓨처스", t.short + "2", t.strength - float(pc["futuresGap"]), "balanced", cfg, rng)
		lg._fill_roster(cfg, t, rng)
		lg.teams.append(t)
		lg.by_id[t.id] = t
	if rival != null:
		lg.add_rival(cfg, rival)
	for t: ProTeam in lg.teams:
		lg.sort_lineup(t)
	return lg


func team(id: String) -> ProTeam:
	return by_id[id]


func _next_id(t: ProTeam, kind: String) -> String:
	_pid += 1
	return "%s:%s%d" % [t.id, kind, _pid]


## 명단 채우기: 주전 9 + 후보 batBench, 선발 5 + 불펜 bullpen. 능력치 = 팀 전력 ± ratingSd, 나이는 고르게
func _fill_roster(cfg: CareerConfig, t: ProTeam, rng: RandomNumberGenerator) -> void:
	var pc := cfg.pro
	for i in 9:
		t.lineup.append(_new_batter(cfg, t, Team.POSITIONS[i], t.strength, rng.randi_range(int(pc["ageMin"]), int(pc["ageMax"])), 0, rng))
	for i in int(pc["batBench"]):
		t.bench.append(_new_batter(cfg, t, Team.POSITIONS[rng.randi_range(0, 7)], t.strength - float(pc["benchGap"]),
			rng.randi_range(int(pc["ageMin"]), int(pc["ageMax"])), 0, rng))
	for i in 5:
		t.starters.append(_new_pitcher(cfg, t, "선발", t.strength + float(pc["starterEdge"]), rng.randi_range(int(pc["ageMin"]), int(pc["ageMax"])), 0, rng))
	for i in int(pc["bullpen"]):
		t.bullpen.append(_new_pitcher(cfg, t, "불펜", t.strength, rng.randi_range(int(pc["ageMin"]), int(pc["ageMax"])), 0, rng))


func _new_batter(cfg: CareerConfig, t: ProTeam, pos: String, center: float, age: int, rookie_year: int, rng: RandomNumberGenerator) -> ProPlayer:
	var sd := float(cfg.roster["ratingSd"])
	var skills := BatterSkills.new(Team._rating(center, sd, rng), Team._rating(center, sd, rng), Team._rating(center, sd, rng), Team._rating(center, sd, rng))
	return ProPlayer.make_batter(_next_id(t, "b"), Team._random_name(cfg, rng), t.id, pos, skills, age, rookie_year)


func _new_pitcher(cfg: CareerConfig, t: ProTeam, role: String, center: float, age: int, rookie_year: int, rng: RandomNumberGenerator) -> ProPlayer:
	var sd := float(cfg.roster["ratingSd"])
	var r := cfg.roster
	var max_p := int(r["starterPitches"]) + rng.randi_range(-int(r["starterPitchesJitter"]), int(r["starterPitchesJitter"])) if role == "선발" else int(r["relieverPitches"])
	return ProPlayer.make_pitcher(_next_id(t, "p"), Team._random_name(cfg, rng), t.id, role, Team._rating(center, sd, rng), Team._rating(center, sd, rng),
		Team.PITCHER_PROFILES[rng.randi_range(0, 2)], max_p, age, rookie_year)


## 강태오: 대륙 웨일즈(전체 1순위, worldview 5-3)의 유격수 주전. 능력치는 고교 라이벌 PlayerData 그대로
func add_rival(cfg: CareerConfig, rival: PlayerData) -> ProPlayer:
	var t: ProTeam = by_id[cfg.pro["rivalTeam"]]
	var skills := BatterSkills.new(rival.contact, rival.power, rival.eye, rival.speed)
	var p := ProPlayer.make_batter(_next_id(t, "b"), rival.name, t.id, "유격수", skills, int(cfg.pro["rookieAge"]), year)
	p.is_rival = true
	p.batter.protected = true
	for i in t.lineup.size():
		if (t.lineup[i] as ProPlayer).position == "유격수":
			t.bench.append(t.lineup[i])
			t.lineup[i] = p
			break
	return p


func rival_player() -> ProPlayer:
	for t: ProTeam in teams:
		for p: ProPlayer in t.all_players():
			if p.is_rival:
				return p
	return null


## 타순: 능력치 순으로 3·4·5·1·2·6·7·8·9번 (출루형이 1·2번에 오도록 1·2번은 컨택+선구안 순)
func sort_lineup(t: ProTeam) -> void:
	var by_power: Array = t.lineup.duplicate()
	by_power.sort_custom(func(a: ProPlayer, b: ProPlayer) -> bool: return a.rating() > b.rating())
	var core: Array = by_power.slice(0, 5)
	var rest: Array = by_power.slice(5)
	core.sort_custom(func(a: ProPlayer, b: ProPlayer) -> bool:
		return a.batter.skills.contact + a.batter.skills.eye > b.batter.skills.contact + b.batter.skills.eye)
	# core 의 출루 상위 둘이 1·2번, 나머지 셋이 3·4·5번 (파워 순)
	var top: Array = core.slice(0, 2)
	var mid: Array = core.slice(2)
	mid.sort_custom(func(a: ProPlayer, b: ProPlayer) -> bool: return a.batter.skills.power > b.batter.skills.power)
	t.lineup = top + mid + rest


# ---------- 일정 ----------

## 라운드 r 의 짝 (원형 순환 방식: 9라운드에 모든 팀과 한 번씩). [[원정 id, 홈 id] …]. 순환이 돌 때마다 홈·원정을 바꾼다
func pairings(r: int) -> Array:
	var n := teams.size()
	var ids: Array = []
	for t: ProTeam in teams:
		ids.append(t.id)
	var k := r % (n - 1)
	var rot: Array = [ids[0]]
	for i in n - 1:
		rot.append(ids[1 + (i + k) % (n - 1)])
	var out := []
	var flip := (r / (n - 1)) % 2 == 1
	for i in n / 2:
		var a: String = rot[i]
		var b: String = rot[n - 1 - i]
		if (i + k) % 2 == 1:
			var tmp := a
			a = b
			b = tmp
		out.append([b, a] if flip else [a, b])
	return out


## 이번 주(정규시즌 week, 1부터) 내 팀의 3연전 상대들 [{"opponent", "home", "games"}]
func series_for(cfg: CareerConfig, team_id: String, week: int) -> Array:
	var out := []
	var per := int(cfg.pro["seriesPerWeek"])
	for k in per:
		for pair: Array in pairings((week - 1) * per + k):
			if pair[0] == team_id or pair[1] == team_id:
				out.append({"opponent": pair[1] if pair[0] == team_id else pair[0], "home": pair[1] == team_id,
					"games": int(cfg.pro["gamesPerWeek"]) / per})
	return out


# ---------- 경기 ----------

## 한 경기를 만든다 (아직 돌리지 않음). me / me_slot: 내가 선발로 나오는 팀이면 그 정보 (아니면 null).
## direct: 내가 직접 하는 경기 (GameRunner 가 내 자리를 PlayerData 로 만든다). 돌려주는 값: {"game", "orders": [원정 명단, 홈 명단], "arms": [...]}
func make_game(cfg: CareerConfig, bc: BattingConfig, away: ProTeam, home: ProTeam, salt: String, me_player: PlayerData = null,
		me: ProPlayer = null, me_team: String = "", direct: bool = false) -> Dictionary:
	var away_me: ProPlayer = me if me_team == away.id else null
	var home_me: ProPlayer = me if me_team == home.id else null
	var slot := me_player.lineup_slot if me_player != null else 0
	var orders := [away.set_for_game(away_me, slot), home.set_for_game(home_me, slot)]
	var arms := [away.pitchers_for_game(), home.pitchers_for_game()]
	var game: GameRunner
	var sd := hash([seed, year, salt])
	if direct and me_player != null and (me_team == away.id or me_team == home.id):
		var mine: ProTeam = away if me_team == away.id else home
		var theirs: ProTeam = home if mine == away else away
		game = GameRunner.new(cfg, bc, me_player, mine.school, theirs.school, mine == home, sd, true, false)
	else:
		# 내가 직접 하지 않는 경기: 원정 팀을 "내 팀" 자리에 두고 결장으로 만든다 (명단 그대로 자동 진행)
		var dummy := me_player if me_player != null else PlayerData.new()
		game = GameRunner.new(cfg, bc, dummy, away.school, home.school, false, sd, false, false)
	return {"game": game, "orders": orders, "arms": arms, "away": away.id, "home": home.id}


## 끝난 경기를 기록에 반영한다: 선수 기록(박스스코어 줄 → ProPlayer), 승·패 투수, 팀 성적. post: 포스트시즌 경기면 라운드 id.
## my_line: 내가 직접 한 경기면 내 기록(game.my_line — 도루·수비까지 있다). 돌려주는 값: 결과 {"away", "home", "score": [원정, 홈], "lines": {선수 id: SeasonStats}}
func record_game(m: Dictionary, post: String = "", my_line: PlayerData.SeasonStats = null) -> Dictionary:
	var game: GameRunner = m["game"]
	var st := game.state
	var away: ProTeam = by_id[m["away"]]
	var home: ProTeam = by_id[m["home"]]
	var lines := {}
	for side in 2:
		var order: Array = m["orders"][side]
		for i in order.size():
			var p: ProPlayer = order[i]
			var ln: PlayerData.SeasonStats
			if p.is_me and my_line != null:
				ln = my_line
			else:
				ln = PlayerData.SeasonStats.from_box(game.box.batters[side][i])
			lines[p.id] = ln
			_add_batting(p, ln, post)
		var arms: Array = m["arms"][side]
		for i in game.box.pitchers[side].size():
			var pl: BoxScore.PitcherLine = game.box.pitchers[side][i]
			if pl.bf == 0:
				continue
			var pp: ProPlayer = arms[i]
			if post == "":
				pp.pitching.add_line(pl, i == 0)
			else:
				pp.pitching_post.add_line(pl, i == 0)
	# 승·패 투수: 이긴 팀 선발이 5이닝(15아웃) 넘게 던졌으면 선발, 아니면 가장 오래 던진 불펜 / 진 팀은 가장 많이 내준 투수
	var score: Array = [st.score[0], st.score[1]]
	if score[0] != score[1]:
		var win_side := 0 if score[0] > score[1] else 1
		_decide(game, m["arms"], win_side, true, post)
		_decide(game, m["arms"], 1 - win_side, false, post)
	if post == "":
		var res := signi(score[0] - score[1])
		away.add_result(res, score[0], score[1])
		home.add_result(-res, score[1], score[0])
	away.next_rotation()
	home.next_rotation()
	return {"away": away.id, "home": home.id, "score": score, "lines": lines}


func _add_batting(p: ProPlayer, ln: PlayerData.SeasonStats, post: String) -> void:
	if post == "":
		p.season.merge(ln)
	else:
		p.post.merge(ln)
		if post == "ks":
			p.ks.merge(ln)


func _decide(game: GameRunner, arms: Array, side: int, win: bool, post: String) -> void:
	var lines: Array = game.box.pitchers[side]
	var pick := -1
	if win:
		if lines.size() > 0 and (lines[0] as BoxScore.PitcherLine).outs >= 15:
			pick = 0
		else:
			var best := -1
			for i in range(1, lines.size()):
				var pl: BoxScore.PitcherLine = lines[i]
				if pl.bf > 0 and pl.outs > best:
					best = pl.outs
					pick = i
			if pick < 0:
				pick = 0
	else:
		var worst := -1
		for i in lines.size():
			var r: int = (lines[i] as BoxScore.PitcherLine).r
			if (lines[i] as BoxScore.PitcherLine).bf > 0 and r > worst:
				worst = r
				pick = i
	if pick < 0:
		return
	var pp: ProPlayer = arms[side][pick]
	var ps := pp.pitching if post == "" else pp.pitching_post
	if win:
		ps.wins += 1
	else:
		ps.losses += 1


## 정규시즌 week 주차 d 번째 날(0 화 … 5 일)의 경기들 [{"away", "home", "salt"}]. 3연전 seriesPerWeek 개가 한 주:
## d / 3 번째 3연전의 g = d % 3 번째 경기. 10구단이라 날마다 모든 팀이 한 경기씩 한다
func day_pairs(cfg: CareerConfig, week: int, d: int) -> Array:
	var per := int(cfg.pro["seriesPerWeek"])
	var games_per := int(cfg.pro["gamesPerWeek"]) / per
	var r := (week - 1) * per + d / games_per
	var g := d % games_per
	var out := []
	for pair: Array in pairings(r):
		out.append({"away": pair[0], "home": pair[1], "salt": "w%d:r%d:%s:%d" % [week, r, pair[0], g]})
	return out


## 그날 이 팀의 경기 {"away", "home", "salt", "opponent", "is_home"}
func team_game_on(cfg: CareerConfig, team_id: String, week: int, d: int) -> Dictionary:
	for p: Dictionary in day_pairs(cfg, week, d):
		if p["away"] == team_id or p["home"] == team_id:
			var out := p.duplicate()
			out["is_home"] = p["home"] == team_id
			out["opponent"] = p["away"] if out["is_home"] else p["home"]
			return out
	return {}


## 그날 다른 팀 경기를 모두 돌린다 (skip_team: 따로 돌리는 내 팀). 돌려주는 값: 경기 결과 목록
func sim_day_others(cfg: CareerConfig, bc: BattingConfig, week: int, d: int, skip_team: String) -> Array:
	var out := []
	for p: Dictionary in day_pairs(cfg, week, d):
		if p["away"] == skip_team or p["home"] == skip_team:
			continue
		var m := make_game(cfg, bc, by_id[p["away"]], by_id[p["home"]], p["salt"])
		(m["game"] as GameRunner).run_to_end()
		out.append(record_game(m))
	return out


## 정규시즌 한 주의 다른 팀 경기를 모두 (날마다 차례로 — 튜닝 도구·테스트용)
func sim_week_others(cfg: CareerConfig, bc: BattingConfig, week: int, skip_team: String) -> Array:
	var out := []
	for d in int(cfg.pro["gamesPerWeek"]):
		out.append_array(sim_day_others(cfg, bc, week, d, skip_team))
	return out


# ---------- 순위 ----------

## 순위표 (승률 순, 같으면 승 많은 순, 그래도 같으면 content 순서) [ProTeam]
func standings() -> Array:
	var rows: Array = teams.duplicate()
	rows.sort_custom(func(a: ProTeam, b: ProTeam) -> bool:
		if not is_equal_approx(a.pct(), b.pct()):
			return a.pct() > b.pct()
		if a.w != b.w:
			return a.w > b.w
		return teams.find(a) < teams.find(b))
	return rows


func rank_of(team_id: String) -> int:
	var rows := standings()
	for i in rows.size():
		if (rows[i] as ProTeam).id == team_id:
			return i + 1
	return 0


## 1위와의 승차 (KBO 식: ((1위 승 − 이 팀 승) + (이 팀 패 − 1위 패)) / 2)
func games_behind(t: ProTeam) -> float:
	var top: ProTeam = standings()[0]
	return ((top.w - t.w) + (t.l - top.l)) / 2.0


# ---------- 개인 기록 ----------

## 규정 타석 (팀 경기 수 × 3.1) · 규정 이닝 (팀 경기 수 × 1 — 아웃 수로는 × 3)
func qualified_pa(cfg: CareerConfig) -> int:
	return int(floor(_team_games() * float(cfg.pro["qualifyPaPerGame"])))


func qualified_outs(cfg: CareerConfig) -> int:
	return int(floor(_team_games() * float(cfg.pro["qualifyIpPerGame"]) * 3.0))


func _team_games() -> int:
	var most := 0
	for t: ProTeam in teams:
		most = maxi(most, t.games())
	return most


func all_batters() -> Array:
	var out := []
	for t: ProTeam in teams:
		for p: ProPlayer in t.lineup + t.bench:
			out.append(p)
	return out


func all_pitchers() -> Array:
	var out := []
	for t: ProTeam in teams:
		for p: ProPlayer in t.starters + t.bullpen:
			out.append(p)
	return out


## 리그 평균 OPS · 9이닝당 실점 (기여도 기준)
func league_ops() -> float:
	var s := PlayerData.SeasonStats.new()
	for p: ProPlayer in all_batters():
		s.merge(p.season)
	return s.obp() + s.slg() if s.ab > 0 else 0.7


func league_ra9() -> float:
	var outs := 0
	var r := 0
	for p: ProPlayer in all_pitchers():
		outs += p.pitching.outs
		r += p.pitching.r
	return r * 27.0 / outs if outs > 0 else 4.5


## 부문별 순위 [{"player": ProPlayer, "value": 숫자, "text": 글자}] 상위 n 명.
## stat: avg obp slg ops (규정 타석 이상) · hr rbi hits sb · era (규정 이닝 이상, 낮을수록) · wins so
func leaders(cfg: CareerConfig, stat: String, n: int = 10) -> Array:
	var rows := []
	var qpa := qualified_pa(cfg)
	var qouts := qualified_outs(cfg)
	var pitching := stat in ["era", "wins", "so"]
	for p: ProPlayer in (all_pitchers() if pitching else all_batters()):
		var s := p.season
		var v := 0.0
		var text := ""
		match stat:
			"avg":
				if s.pa < qpa or s.ab == 0:
					continue
				v = float(s.hits) / s.ab
				text = s.average()
			"obp":
				if s.pa < qpa:
					continue
				v = s.obp()
				text = s.obp_text()
			"slg":
				if s.pa < qpa:
					continue
				v = s.slg()
				text = s.slg_text()
			"ops":
				if s.pa < qpa:
					continue
				v = s.obp() + s.slg()
				text = s.ops()
			"hr":
				v = s.home_runs
			"rbi":
				v = s.rbi
			"hits":
				v = s.hits
			"sb":
				v = s.steals
			"era":
				if p.pitching.outs < qouts or p.pitching.outs == 0:
					continue
				v = -p.pitching.era()
				text = p.pitching.era_text()
			"wins":
				v = p.pitching.wins
			"so":
				v = p.pitching.so
		if text == "":
			text = str(int(v))
		if not stat in ["avg", "obp", "slg", "ops", "era"] and v <= 0:
			continue
		rows.append({"player": p, "value": v, "text": text})
	rows.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		if not is_equal_approx(a["value"], b["value"]):
			return a["value"] > b["value"]
		return (a["player"] as ProPlayer).id < (b["player"] as ProPlayer).id)
	return rows.slice(0, n)


## 이 선수가 그 부문 몇 위인가 (순위 밖이면 0)
func leader_rank(cfg: CareerConfig, stat: String, p: ProPlayer, n: int = 30) -> int:
	var rows := leaders(cfg, stat, n)
	for i in rows.size():
		if rows[i]["player"] == p:
			return i + 1
	return 0


# ---------- 포스트시즌 ----------

func season_over(cfg: CareerConfig) -> bool:
	return weeks_done >= int(cfg.pro["weeks"])


func post_over() -> bool:
	return post_round >= POST_ROUNDS.size()


## 정규시즌을 닫고 순위를 고정한다
func lock_standings() -> void:
	final_order = []
	for t: ProTeam in standings():
		final_order.append(t.id)


## 이번 포스트시즌 라운드의 두 팀 [윗 시드, 아랫 시드] (앞 라운드 승자가 아랫 시드)
func post_pair() -> Array:
	var r: String = POST_ROUNDS[post_round]
	match r:
		"wildcard":
			return [final_order[3], final_order[4]]
		"semi":
			return [final_order[2], post_results[-1]["winner"]]
		"playoff":
			return [final_order[1], post_results[-1]["winner"]]
	return [final_order[0], post_results[-1]["winner"]]


## 이번 라운드를 시작한다 (아직이면): {"round", "hi", "lo", "wins": [윗 시드, 아랫 시드], "g": 다음 경기 번호, "games": [{"hi", "lo"}]}
func post_begin(cfg: CareerConfig) -> Dictionary:
	if post_state.is_empty() and not post_over():
		var r: String = POST_ROUNDS[post_round]
		var pair := post_pair()
		post_state = {"round": r, "hi": pair[0], "lo": pair[1], "wins": [int(cfg.pro["postseason"][r].get("hiStartWins", 0)), 0], "g": 0, "games": []}
	return post_state


## 이번 라운드 다음 경기를 만든다 (1·2·5·7차전은 윗 시드 홈 — KBO 2-2-1 / 2-3-2 를 단순화). direct: 내가 직접 하는 경기
func post_next_match(cfg: CareerConfig, bc: BattingConfig, me_player: PlayerData = null, me: ProPlayer = null, me_team: String = "",
		direct: bool = false) -> Dictionary:
	var st := post_begin(cfg)
	var hi: ProTeam = by_id[st["hi"]]
	var lo: ProTeam = by_id[st["lo"]]
	var g: int = st["g"]
	var hi_home := g in [0, 1, 4, 6]
	return make_game(cfg, bc, lo if hi_home else hi, hi if hi_home else lo, "post%d:%s:%d" % [year, st["round"], g], me_player, me, me_team, direct)


## 이번 라운드 경기 하나의 결과를 넣는다. 돌려주는 값: record_game 결과 + "decided" (라운드가 끝났나) + "result" (끝났으면 라운드 결과)
func post_record(cfg: CareerConfig, m: Dictionary, my_line: PlayerData.SeasonStats = null) -> Dictionary:
	var st := post_begin(cfg)
	var r: String = st["round"]
	var rule: Dictionary = cfg.pro["postseason"][r]
	var res := record_game(m, r, my_line)
	var hi_home: bool = res["home"] == st["hi"]
	var hi_score: int = res["score"][1] if hi_home else res["score"][0]
	var lo_score: int = res["score"][0] if hi_home else res["score"][1]
	var wins: Array = st["wins"]
	if hi_score > lo_score:
		wins[0] += 1
	elif lo_score > hi_score:
		wins[1] += 1
	(st["games"] as Array).append({"hi": hi_score, "lo": lo_score})
	st["g"] = int(st["g"]) + 1
	var need := int(rule["need"])
	res["decided"] = int(wins[0]) >= need or int(wins[1]) >= need or int(st["g"]) >= int(rule["maxGames"])
	if res["decided"]:
		# 정해진 경기를 다 해도 못 가렸으면(무승부) 윗 시드가 올라간다
		var winner: String = st["lo"] if int(wins[1]) >= need else st["hi"]
		var out := {"round": r, "hi": st["hi"], "lo": st["lo"], "wins": wins.duplicate(), "winner": winner, "games": (st["games"] as Array).duplicate()}
		post_results.append(out)
		post_round += 1
		post_state = {}
		if r == "ks":
			champions.append({"year": year, "team": winner, "runner": out["lo"] if winner == out["hi"] else out["hi"]})
		res["result"] = out
	return res


## 포스트시즌 한 라운드를 끝까지 (튜닝 도구·테스트·자동 진행용). first: 이미 끝난 1차전(내가 직접 한 경기)이 있으면 그것부터.
## 돌려주는 값: 이 라운드 결과
func play_post_round(cfg: CareerConfig, bc: BattingConfig, first: Dictionary = {}, me_player: PlayerData = null, me: ProPlayer = null,
		me_team: String = "", my_line: PlayerData.SeasonStats = null) -> Dictionary:
	post_begin(cfg)
	while true:
		var res: Dictionary
		if not first.is_empty():
			res = post_record(cfg, first, my_line)
			first = {}
		else:
			var m := post_next_match(cfg, bc, me_player, me, me_team, false)
			(m["game"] as GameRunner).run_to_end()
			res = post_record(cfg, m)
		if res["decided"]:
			return res["result"]
	return {}


## 이 팀의 올해 포스트시즌 결과 글자 ("우승" | "준우승" | "플레이오프 탈락" … | "" 진출 실패)
func post_text(team_id: String) -> String:
	var labels: Dictionary = {"wildcard": "와일드카드", "semi": "준플레이오프", "playoff": "플레이오프", "ks": "한국시리즈"}
	var text := ""
	for res: Dictionary in post_results:
		if res["hi"] != team_id and res["lo"] != team_id:
			continue
		if res["winner"] != team_id:
			return "준우승" if res["round"] == "ks" else "%s 탈락" % labels[res["round"]]
		text = "우승" if res["round"] == "ks" else "%s 승리" % labels[res["round"]]
	return text


## 아직 포스트시즌에 남아 있나 (이번 라운드 두 팀 중 하나)
func in_post(team_id: String) -> bool:
	if post_over() or final_order.is_empty():
		return false
	return team_id in post_pair()


# ---------- 시상 ----------

## 시즌 시상 (포스트시즌이 끝난 뒤): MVP · 신인왕 · 포지션별 골든글러브 · 한국시리즈 MVP · 타이틀(타격·홈런·타점·도루·다승·평균자책·탈삼진).
## 돌려주는 값: [{"year", "award", "name", "team", "line", "id"}] (awards 에도 더한다)
func give_awards(cfg: CareerConfig) -> Array:
	var out := []
	var lops := league_ops()
	var lra := league_ra9()
	var qpa := qualified_pa(cfg)
	var everyone: Array = all_batters() + all_pitchers()
	var best_of := func(pool: Array) -> ProPlayer:
		var best: ProPlayer = null
		var bv := -INF
		for p: ProPlayer in pool:
			var v := p.value(lops, lra)
			if v > bv:
				bv = v
				best = p
		return best
	var mvp: ProPlayer = best_of.call(everyone)
	if mvp != null:
		out.append(_award("MVP", mvp))
	var rookies := everyone.filter(func(p: ProPlayer) -> bool: return p.rookie_year == year)
	var roy: ProPlayer = best_of.call(rookies)
	if roy != null:
		out.append(_award("신인왕", roy))
	# 골든글러브: 포지션마다 (경기 수 절반 이상 나온 타자 중), 투수 하나
	var half := _team_games() / 2
	for pos: String in Team.POSITIONS:
		var pool := all_batters().filter(func(p: ProPlayer) -> bool: return p.position == pos and p.season.games >= half)
		var gg: ProPlayer = best_of.call(pool)
		if gg != null:
			out.append(_award("골든글러브 " + pos, gg))
	var gp: ProPlayer = best_of.call(all_pitchers())
	if gp != null:
		out.append(_award("골든글러브 투수", gp))
	if not champions.is_empty() and int(champions[-1]["year"]) == year:
		var champ: ProTeam = by_id[champions[-1]["team"]]
		var ks_best: ProPlayer = null
		var kv := -1.0
		for p: ProPlayer in champ.lineup + champ.bench:
			var v := (p.ks.obp() + p.ks.slg()) * p.ks.pa + p.ks.home_runs * 2.0
			if p.ks.pa > 0 and v > kv:
				kv = v
				ks_best = p
		if ks_best != null:
			out.append(_award("한국시리즈 MVP", ks_best))
	for pair: Array in [["avg", "타격왕"], ["hr", "홈런왕"], ["rbi", "타점왕"], ["sb", "도루왕"], ["wins", "다승왕"], ["era", "평균자책점 1위"], ["so", "탈삼진왕"]]:
		var top := leaders(cfg, pair[0], 1)
		if not top.is_empty():
			var a := _award(pair[1], top[0]["player"])
			a["line"] = "%s %s" % [pair[1].trim_suffix("왕").trim_suffix(" 1위"), top[0]["text"]]
			out.append(a)
	awards.append_array(out)
	return out


func _award(award: String, p: ProPlayer) -> Dictionary:
	return {"year": year, "award": award, "name": p.name, "team": by_id[p.team_id].name, "line": p.line_text(), "id": p.id}


## 이 선수가 올해 받은 상 이름들
func awards_of(player_id: String, y: int = -1) -> Array:
	var out := []
	for a: Dictionary in awards:
		if a["id"] == player_id and (y < 0 or int(a["year"]) == y):
			out.append(a["award"])
	return out


# ---------- 시즌 끝 ----------

## 다음 시즌: 기록을 역사로, 나이 한 살 + 나이별 성장·노쇠, 은퇴·신인 입단, 전력 재계산, 순위·포스트시즌 초기화
func next_season(cfg: CareerConfig) -> void:
	var pc := cfg.pro
	var rng := _rng("offseason")
	var qpa := qualified_pa(cfg)
	for t: ProTeam in teams:
		t.update_bests(year, qpa)
		var post := post_text(t.id)
		t.close_season(year, final_order.find(t.id) + 1, post)
		for p: ProPlayer in t.all_players():
			p.close_season(year, t.name)
			p.age += 1
			if not p.is_me:
				_age_player(cfg, p, rng)
		# 은퇴 (나이 retireAge 이상 — 강태오는 retire.rivalAge, 내 선수는 은퇴 카드로) → 기록을 남기고 같은 자리에 신인
		for list: Array in [t.lineup, t.bench, t.starters, t.bullpen]:
			for i in list.size():
				var p: ProPlayer = list[i]
				if p.is_me or p.age < int(pc["retire"]["rivalAge"] if p.is_rival else pc["retireAge"]):
					continue
				archive(p, "은퇴")
				if p.is_pitcher:
					list[i] = _new_pitcher(cfg, t, p.position, t.strength - float(pc["rookieGap"]), int(pc["rookieAge"]), year + 1, rng)
				else:
					list[i] = _new_batter(cfg, t, p.position, t.strength - float(pc["rookieGap"]), int(pc["rookieAge"]), year + 1, rng)
		# 해마다 신인 한 명은 늘 들어온다 (후보 타자 맨 끝 자리 — 신인왕 경쟁자)
		if not t.bench.is_empty():
			var last: ProPlayer = t.bench[-1]
			if not last.is_me and not last.is_rival:
				archive(last, "방출")
				t.bench[-1] = _new_batter(cfg, t, last.position, t.strength - float(pc["rookieGap"]) + rng.randfn(0.0, 4.0), int(pc["rookieAge"]), year + 1, rng)
		_promote_bench(t)
		t.strength = clampf(_team_strength(t) + rng.randfn(0.0, float(pc["strengthDriftSd"])), 40.0, 80.0)
		sort_lineup(t)
	year += 1
	weeks_done = 0
	post_round = 0
	post_results = []
	post_state = {}
	final_order = []
	week_events = []


# ---------- 역대 기록 ----------

## 은퇴·방출하는 선수의 기록을 남긴다 (통산 = 지난 시즌들 + 아직 닫지 않은 이번 시즌)
func archive(p: ProPlayer, why: String) -> void:
	var c := PlayerData.SeasonStats.new()
	c.merge(p.career)
	c.merge(p.season)
	var pc := ProPlayer.PitchStats.new()
	pc.merge(p.pitching_career)
	pc.merge(p.pitching)
	retired.append({"id": p.id, "name": p.name, "team": by_id[p.team_id].name if by_id.has(p.team_id) else "", "first": p.rookie_year,
		"last": year, "pitcher": p.is_pitcher, "games": c.games if not p.is_pitcher else pc.games, "hits": c.hits, "hr": c.home_runs,
		"rbi": c.rbi, "sb": c.steals, "avg": c.average(), "wins": pc.wins, "so": pc.so, "why": why, "me": p.is_me, "rival": p.is_rival})


## 역대 순위 (은퇴한 선수 + 지금 뛰는 선수의 통산). stat: hits | hr | rbi | sb (타자) · wins | so (투수).
## 돌려주는 값: [{"id", "name", "team", "value", "active", "me"}] 큰 순서
func all_time(stat: String, n: int = 10) -> Array:
	var pitching := stat in ["wins", "so"]
	var rows := []
	var seen := {}
	for r: Dictionary in retired:
		if bool(r["pitcher"]) != pitching or int(r[stat]) <= 0:
			continue
		rows.append({"id": r["id"], "name": r["name"], "team": r["team"], "value": int(r[stat]), "active": false, "me": r["me"]})
		seen[r["id"]] = true
	for t: ProTeam in teams:
		for p: ProPlayer in t.all_players():
			if p.is_pitcher != pitching or seen.has(p.id):
				continue
			var v := 0
			match stat:
				"hits": v = p.career.hits + p.season.hits
				"hr": v = p.career.home_runs + p.season.home_runs
				"rbi": v = p.career.rbi + p.season.rbi
				"sb": v = p.career.steals + p.season.steals
				"wins": v = p.pitching_career.wins + p.pitching.wins
				"so": v = p.pitching_career.so + p.pitching.so
			if v > 0:
				rows.append({"id": p.id, "name": p.name, "team": t.name, "value": v, "active": true, "me": p.is_me})
	rows.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		if a["value"] != b["value"]:
			return a["value"] > b["value"]
		return str(a["id"]) < str(b["id"]))
	return rows.slice(0, n) if n > 0 else rows


## 이 선수의 역대 순위 (기록이 없으면 0)
func all_time_rank(stat: String, player_id: String) -> int:
	var rows := all_time(stat, 0)
	for i in rows.size():
		if rows[i]["id"] == player_id:
			return i + 1
	return 0


## 나이 곡선의 한 줄 [이 나이까지, 성장 배율, 한 해 변화] (balance.json career.pro.ageCurve.rows)
static func age_row(cfg: CareerConfig, age: int) -> Array:
	var rows: Array = cfg.pro["ageCurve"]["rows"]
	for row: Array in rows:
		if age <= int(row[0]):
			return row
	return rows[-1]


## 리그 선수의 한 해 (그 나이가 되는 시즌 시작에): 나이 곡선의 한 해 변화 × leagueYearlyScale ± 흔들림.
## 20~28세 크게 오르고 33세까지 점점 줄고 34세 유지, 35세부터 내리막. 주력은 떨어지기만 한다 (30세부터 해마다 1 더)
func _age_player(cfg: CareerConfig, p: ProPlayer, rng: RandomNumberGenerator) -> void:
	var delta := float(age_row(cfg, p.age)[2]) * float(cfg.pro["ageCurve"]["leagueYearlyScale"])
	var jitter := float(cfg.pro["growthJitter"])
	if p.is_pitcher:
		p.arm.stuff = clampi(p.arm.stuff + roundi(delta + rng.randfn(0.0, jitter)), 15, 95)
		p.arm.control = clampi(p.arm.control + roundi(delta + rng.randfn(0.0, jitter)), 15, 95)
	else:
		var s := p.batter.skills
		var speed_drop := mini(0, roundi(delta)) - (1 if p.age >= 30 else 0)
		p.batter.skills = BatterSkills.new(clampi(s.contact + roundi(delta + rng.randfn(0.0, jitter)), 15, 95),
			clampi(s.power + roundi(delta + rng.randfn(0.0, jitter)), 15, 95), clampi(s.eye + roundi(delta + rng.randfn(0.0, jitter)), 15, 95),
			clampi(s.speed + speed_drop, 15, 95))


## 후보가 주전보다 낫으면 자리를 바꾼다 (같은 포지션이 아니어도 지명타자 자리는 아무나)
func _promote_bench(t: ProTeam) -> void:
	for bi in t.bench.size():
		var b: ProPlayer = t.bench[bi]
		if b.is_me:
			continue
		var worst := -1
		var wv := INF
		for i in t.lineup.size():
			var p: ProPlayer = t.lineup[i]
			if p.is_me or p.is_rival:
				continue
			if (p.position == b.position or p.position == "지명타자") and p.rating() < wv:
				wv = p.rating()
				worst = i
		if worst >= 0 and b.rating() > wv + 2.0:
			var out: ProPlayer = t.lineup[worst]
			b.position = out.position
			b.batter.position = out.position
			t.lineup[worst] = b
			t.bench[bi] = out


## 팀 전력 = 주전·선발 능력치 평균 (명단이 바뀌면 전력도 바뀐다)
func _team_strength(t: ProTeam) -> float:
	var sum := 0.0
	var n := 0
	for p: ProPlayer in t.lineup + t.starters:
		sum += p.rating()
		n += 1
	return sum / n if n > 0 else t.strength


func _rng(salt: String) -> RandomNumberGenerator:
	var r := RandomNumberGenerator.new()
	r.seed = hash([seed, year, salt])
	return r
