class_name ProCareer
extends RefCounted
## 내 프로 생활: 드래프트 지명 → 계약 → 2군(퓨처스)·1군 오르내림 → 정규시즌 → 포스트시즌 → 시상·연봉 → 다음 시즌.
## 리그(ProLeague)의 한 선수(me)로 들어가서, 내 기록도 리그의 다른 선수와 같은 방식(박스스코어)으로 쌓인다.
##
## 한 주 (정규시즌): 내 팀 6경기 중 첫 경기는 내가 직접 하고(1군이면 1군 경기, 2군이면 퓨처스 경기), 나머지는 자동.
##   자동 경기에도 내 선수는 능력치대로 나와 친다 — 그래서 시즌 기록이 144경기분으로 쌓인다.
##   다른 팀 경기도 모두 같은 주에 돌린다. 주가 끝나면 성장·컨디션·부상·폼, 감독 신뢰 → 콜업·강등, 연봉(주급), 국가대표(ProNational),
##   복무(ProService), 기념 순간(Milestones), 뉴스·SNS. 시즌 결산에서 시상·연봉·FA(ProFA)·입대 카드.
## 1군·퓨처스는 감독 신뢰(trust)로만 오르내린다: 기준 아래(1군)·위(퓨처스)인 채 정해진 경기 수를 지켜보면 움직인다.
## 포스트시즌: 라운드마다 한 주. 내 팀이 나가면 1차전을 직접 하고 나머지는 자동.
## 경기 밖 결정은 고교와 같다: 훈련 칸 + 이야기·문자 (CLAUDE.md §3-4 — 엔트리·계약 서류는 자동).

const LEVEL_FIRST := "first"
const LEVEL_FUTURES := "futures"
## 군 복무 중 (상무 또는 현역 — ProService)
const LEVEL_MILITARY := "military"
const PHASE_REGULAR := "regular"
const PHASE_POST := "post"
## 시즌이 끝나 결산(시상·연봉)만 남았다
const PHASE_DONE := "done"

var league: ProLeague
## 내 구단 id · 리그 속 내 선수
var team_id := ""
var me: ProPlayer
## 드래프트: 관심도 등급(S/A/B/C) · 지명 글자 ("1라운드 전체 2순위") · 계약금(원) · 육성선수인가
var tier := ""
var pick_text := ""
var bonus := 0
var development := false
## 올해 연봉(원) · 연봉 역사 [{"year", "salary"}]
var salary := 0
var salary_history: Array = []
## 1군 | 퓨처스 · 퓨처스에서 보낸 주 · 퓨처스 시즌 기록 · 1군 데뷔를 했나
var level := LEVEL_FUTURES
var futures_weeks := 0
var futures_season := PlayerData.SeasonStats.new()
var debuted := false
var phase := PHASE_REGULAR
## 인기도 (0~100): 활약·상·뉴스로 오른다. SNS 반응 크기·이야기 조건
var fame := 5
## 본 기념 순간 {id: 연도} · 아직 화면에 안 띄운 기념 순간 [정의 Dictionary]
var milestones_done := {}
var pending_scenes: Array = []
## 이번 주 내 경기들 [{"opp", "home", "my", "their", "result", "line": SeasonStats, "direct", "level"}]
var week_games: Array = []
## 뉴스·SNS
var feed: ProFeed
## 이번 시즌 받은 상 · 연봉 협상 결과 글자 (결산 화면)
var season_awards: Array = []
var season_note := ""
## 이번 시즌 결산을 이미 했나 (시상·연봉·FA 를 두 번 하지 않게)
var _closed := false
## 감독 신뢰 (0~100, balance.json career.pro.trust) · 기준을 넘은 채 지켜본 경기 수 (1군: 강등 기준 아래 / 퓨처스: 콜업 기준 위)
var trust := 50.0
var watch_games := 0
## 대졸 입단인가 (FA 시즌 수가 다르다)
var college := false
## 이번 시즌 1군에 있던 주 · FA 등록 시즌 · 남은 계약 해(FA 계약) · FA 결정 대기 · FA 횟수 · 메인 엔딩("" | "stay" | "move")
var first_weeks := 0
var fa_seasons := 0
var contract_years_left := 0
var fa_pending := false
var fa_count := 0
var ending := ""
## 시즌별 내 기여도 (FA 금액) · 지난 시즌 리그 타자 기여도 순위 (세계야구클래식 선발)
var value_history: Array = []
var last_value_rank := 999
## 병역 (ProService): 상태 · 입대할 종류 · 남은 복무 주 · 상무 기록 · 상무 팀
var military := "none"
var enlist_kind := ""
var service_weeks_left := 0
var service_line := PlayerData.SeasonStats.new()
var sangmu: School = null
## 국가대표 (ProNational): 치른 대회 [{"id", "name", "year", "result", "line"}] · 이미 지나간 대회 {"id:year": true}
var national: Array = []
var national_done := {}
## 새 시즌 시작에 생긴 일 (입대 통지 등) — 허브가 첫 주에 보여 준다
var start_notes: Array[String] = []
## 직접 할 경기 (new_game 과 finish_week 사이에만 있다 — 저장하지 않는다)
var _pending := {}


## 드래프트 결과로 프로 생활을 시작한다 (CareerState.start_pro 가 부른다)
static func create(career: CareerState, draft: Dictionary) -> ProCareer:
	var cfg := career.cfg
	var pc := cfg.pro
	var pro := ProCareer.new()
	pro.tier = draft["tier"]
	pro.team_id = draft["team"]
	pro.pick_text = draft["pick"]
	pro.bonus = int(draft["bonus"])
	pro.development = bool(draft.get("development", false))
	pro.salary = int(pc["salary"]["min"])
	pro.salary_history.append({"year": 1, "salary": pro.salary})
	pro.league = ProLeague.create(cfg, hash([cfg.seed, "kba"]), career.rival.player if career.rival != null else null)
	var t := pro.league.team(pro.team_id)
	var p := career.player
	var age := int(pc["rookieAge"]) + (int(pc["college"]["years"]) if draft.get("college", false) else 0)
	pro.me = ProPlayer.make_batter("%s:me" % t.id, p.name, t.id, "유격수", p.skills_for_game(cfg), age, 1)
	pro.me.is_me = true
	t.bench.push_front(pro.me)
	pro.level = LEVEL_FIRST if pro.tier in pc["startFirst"] else LEVEL_FUTURES
	pro.trust = float(pc["trust"]["startByTier"].get(pro.tier, 45))
	pro.college = bool(draft.get("college", false))
	p.vars["pro_age"] = age
	p.vars["military_status"] = pro.military
	p.vars["pro_trust"] = int(pro.trust)
	pro.feed = ProFeed.new()
	p.money += pro.bonus
	p.season = pro.me.season
	p.lineup_slot = int(pc["debutSlot"])
	return pro


func team() -> ProTeam:
	return league.team(team_id)


func year() -> int:
	return league.year


## 정규시즌 몇 주차 (1부터)
func week_no() -> int:
	return league.weeks_done + 1


func is_first() -> bool:
	return level == LEVEL_FIRST


func level_text() -> String:
	match level:
		LEVEL_FIRST:
			return "1군"
		LEVEL_MILITARY:
			return "상무 복무" if military == ProService.SANGMU else "현역 복무"
	return "퓨처스(2군)"


## 지금 지켜보는 쪽: {"kind": "demote"|"callup"|"", "games", "need"} (화면용)
func watch_state(cfg: CareerConfig) -> Dictionary:
	var tc: Dictionary = cfg.pro["trust"]
	if level == LEVEL_FIRST and trust < float(tc["demoteBelow"]):
		return {"kind": "demote", "games": watch_games, "need": int(tc["watchGames"])}
	if level == LEVEL_FUTURES and trust >= float(tc["callupAbove"]):
		return {"kind": "callup", "games": watch_games, "need": int(tc["callupWatchGames"])}
	return {"kind": "", "games": 0, "need": 0}


## 화면용 한 줄: "정규시즌 7/24주 · 1군" | "포스트시즌 · 준플레이오프" | "시즌 결산"
func phase_text(cfg: CareerConfig) -> String:
	match phase:
		PHASE_REGULAR:
			return "정규시즌 %d/%d주 · %s" % [week_no(), int(cfg.pro["weeks"]), level_text()]
		PHASE_POST:
			return "포스트시즌 · %s" % cfg.pro["postseason"][ProLeague.POST_ROUNDS[league.post_round]]["name"] if not league.post_over() else "포스트시즌 끝"
	return "시즌 결산"


# ---------- 경기 ----------

## 이번 주 내가 직접 할 경기가 있나 (다쳤거나, 포스트시즌에 내 팀이 없거나, 퓨처스인데 포스트시즌이면 없다)
func has_direct_game(career: CareerState) -> bool:
	if career.player.is_injured() or career.game_done:
		return false
	match phase:
		PHASE_REGULAR:
			# 현역 복무 중엔 경기가 없다 (상무는 상무 경기)
			return level != LEVEL_MILITARY or military == ProService.SANGMU
		PHASE_POST:
			return is_first() and league.in_post(team_id)
	return false


## 이번 주 직접 할 경기의 상대 (화면용, 없으면 null)
func next_opponent(cfg: CareerConfig) -> ProTeam:
	if phase == PHASE_POST:
		if not league.in_post(team_id):
			return null
		var pair := league.post_pair()
		return league.team(pair[1] if pair[0] == team_id else pair[0])
	var series := league.series_for(cfg, team_id, week_no())
	return league.team(series[0]["opponent"]) if not series.is_empty() else null


## 직접 할 경기를 만든다 (GameScreen 이 CareerState.new_game 으로 부른다)
func new_game(career: CareerState) -> GameRunner:
	var cfg := career.cfg
	_sync_me(career)
	var opp := next_opponent(cfg)
	var t := team()
	if is_first() or phase == PHASE_POST:
		var home := false
		if phase == PHASE_REGULAR:
			home = bool(league.series_for(cfg, team_id, week_no())[0]["home"])
		else:
			home = league.post_pair()[0] == team_id  # 1차전은 윗 시드 홈
		var away_t: ProTeam = opp if home else t
		var home_t: ProTeam = t if home else opp
		_pending = league.make_game(cfg, career.batting, away_t, home_t, "direct%d:%d:%s" % [league.year, career.total_week, phase],
			career.player, me, team_id, true)
		return _pending["game"]
	# 퓨처스 경기: 내 팀 퓨처스 명단에 내가 들어가 상대 퓨처스와 (상무 복무 중이면 상무 팀으로)
	var sangmu_game := level == LEVEL_MILITARY
	_pending = {"futures": true, "sangmu": sangmu_game}
	var mine: School = ProService.sangmu_school(career, self) if sangmu_game else t.futures
	var g := GameRunner.new(cfg, career.batting, career.player, mine, opp.futures if opp != null else t.futures,
		career.week % 2 == 0, hash([league.seed, league.year, career.total_week, "futures"]), true, false)
	return g


## 내 능력치를 리그 명단 속 내 타자에 맞춘다 (컨디션·폼·장비까지 — 자동 경기에서도 지금 몸 상태로 친다)
func _sync_me(career: CareerState) -> void:
	me.batter.skills = career.player.skills_for_game(career.cfg)
	me.name = career.player.name
	me.batter.name = career.player.name


## 한 주를 마친다. game: 내가 직접 한 경기 (없으면 null — 다쳤거나 포스트시즌에 없을 때). 돌려주는 값: 변화 설명
func finish_week(career: CareerState, game: GameRunner) -> Array[String]:
	var cfg := career.cfg
	var notes: Array[String] = []
	week_games = []
	_sync_me(career)
	var playing := not career.player.is_injured()
	var lines: Array = []  # 이번 주 내 경기 줄
	if phase == PHASE_POST:
		_finish_post_week(career, game, lines, notes)
	else:
		_finish_regular_week(career, game, playing, lines, notes)
	# 이번 주 내 기록으로 성장·폼 (고교와 같은 규칙)
	var week_line := PlayerData.SeasonStats.new()
	for ln: PlayerData.SeasonStats in lines:
		week_line.merge(ln)
	if week_line.pa > 0:
		var raised := Growth.from_line(cfg, career.player, week_line)
		for stat: String in raised:
			notes.append("%s +%d" % [PlayerData.STAT_LABELS[stat], raised[stat]])
		career._update_form(week_line)
		career.player.add_condition(int(cfg.pro["conditionPerWeek"]))
		fame = clampi(fame + week_line.hits / 3 + week_line.home_runs * 2 * (1 if is_first() else 0), 0, 100)
	if not start_notes.is_empty():
		notes.append_array(start_notes)
		start_notes.clear()
	career._roll_injury(notes)
	# 주급 (연봉 ÷ 정규시즌 주 수)
	if phase == PHASE_REGULAR or (phase == PHASE_POST and league.post_results.size() <= 1):
		var pay := salary / int(cfg.pro["weeks"])
		if phase == PHASE_REGULAR:
			career.player.money += pay
			career.last_income = [["주급", pay]]
	_update_trust(career, week_line, lines.size())
	_check_level(career, lines.size(), notes)
	Milestones.check(career, self, lines)
	feed.after_week(career, self, lines)
	league.week_events = []
	career.last = {"result": _week_result(), "hits": week_line.hits, "home_runs": week_line.home_runs, "phase_end": "",
		"national": false, "opponent_id": "", "season_over": phase == PHASE_DONE}
	_pending = {}
	return notes


func _week_result() -> String:
	var r := 0
	for g: Dictionary in week_games:
		r += int(g["result"])
	return "win" if r > 0 else ("loss" if r < 0 else "draw")


func _finish_regular_week(career: CareerState, game: GameRunner, playing: bool, lines: Array, notes: Array[String]) -> void:
	var cfg := career.cfg
	var t := team()
	var wk := week_no()
	var direct_used := false
	if game != null and not _pending.is_empty():
		if _pending.has("futures"):
			var into: PlayerData.SeasonStats = service_line if _pending.get("sangmu", false) else futures_season
			into.merge(game.my_line)
			into.games += 1
			lines.append(game.my_line)
			week_games.append(_game_row(game.opponent.name, game.my_side == GameState.HOME, game.state.score[game.my_side],
				game.state.score[1 - game.my_side], game.my_line, true, LEVEL_FUTURES))
		else:
			var res := league.record_game(_pending, "", game.my_line)
			me.season.games += 1  # 박스스코어 줄(from_box)은 games 를 1로 넣지만 my_line 은 0이다
			lines.append(game.my_line)
			week_games.append(_row_from(res, true))
			_mark_debut(career, notes)
			direct_used = true
	# 내 팀 1군 나머지 경기 (내가 1군이고 건강하면 나도 나온다)
	var me_in := is_first() and playing
	var gi := 0
	for s: Dictionary in league.series_for(cfg, team_id, wk):
		for g in int(s["games"]):
			gi += 1
			if gi == 1 and direct_used:
				continue
			var opp: ProTeam = league.team(s["opponent"])
			var away: ProTeam = opp if s["home"] else t
			var home: ProTeam = t if s["home"] else opp
			var m := league.make_game(cfg, career.batting, away, home, "mine%d:%d:%d" % [league.year, wk, gi],
				career.player if me_in else null, me if me_in else null, team_id if me_in else "", false)
			(m["game"] as GameRunner).run_to_end()
			var res := league.record_game(m)
			if me_in:
				var ln: PlayerData.SeasonStats = res["lines"].get(me.id, null)
				if ln != null:
					lines.append(ln)
				_mark_debut(career, notes)
			week_games.append(_row_from(res, false))
	# 퓨처스면 퓨처스 경기를 더 (내 기록만 센다 — 직접 한 1경기 + 자동 futuresAutoGames 경기)
	if level == LEVEL_FUTURES and playing:
		for i in int(cfg.pro["futuresAutoGames"]):
			var opp_t: ProTeam = league.teams[(wk + i + 1) % league.teams.size()]
			if opp_t.id == team_id:
				opp_t = league.teams[(wk + i + 2) % league.teams.size()]
			var fg := GameRunner.new(cfg, career.batting, career.player, t.futures, opp_t.futures, i % 2 == 0,
				hash([league.seed, league.year, wk, i, "futures-auto"]), false, false)
			# 내 자리에 나를 넣어 자동으로 (결장 경기로 만든 뒤 명단을 바꾼다)
			fg.teams[fg.my_side].lineup[career.player.lineup_slot - 1] = me.batter
			fg.run_to_end()
			var ln := PlayerData.SeasonStats.from_box(fg.box.batters[fg.my_side][career.player.lineup_slot - 1])
			futures_season.merge(ln)
			lines.append(ln)
		futures_weeks += 1
	if is_first():
		first_weeks += 1
	# 복무 중 한 주 (상무 자동 경기 · 현역 녹슮 · 남은 주 → 전역)
	ProService.week(career, self, lines, notes)
	league.sim_week_others(cfg, career.batting, wk, team_id)
	league.weeks_done += 1
	_week_team_events()
	# 국가대표 (이번 주가 대회 주면)
	ProNational.week(career, self, notes)
	if league.season_over(cfg):
		league.lock_standings()
		phase = PHASE_POST
		var rank := league.rank_of(team_id)
		notes.append("정규시즌 %d위로 마감 (%d승 %d패 %d무)" % [rank, t.w, t.l, t.d])
		if rank > int(cfg.pro["postseasonTeams"]):
			notes.append("가을야구 진출 실패")


func _finish_post_week(career: CareerState, game: GameRunner, lines: Array, notes: Array[String]) -> void:
	var cfg := career.cfg
	if league.post_over():
		phase = PHASE_DONE
		return
	var mine := league.in_post(team_id)
	var me_in := mine and is_first() and not career.player.is_injured()
	var first := {}
	if game != null and not _pending.is_empty() and not _pending.has("futures"):
		first = _pending
		_mark_post_debut(career)
	var res := league.play_post_round(cfg, career.batting, first, career.player if me_in else null, me if me_in else null,
		team_id if me_in else "", game.my_line if game != null and not first.is_empty() else null)
	if me_in:
		lines.append(_post_delta(me))
	var name: String = cfg.pro["postseason"][res["round"]]["name"]
	var w: ProTeam = league.team(res["winner"])
	var loser: ProTeam = league.team(res["lo"] if res["winner"] == res["hi"] else res["hi"])
	league.week_events.append({"kind": "post", "round": name, "winner": w.name, "loser": loser.name,
		"wins": "%d승 %d패" % [maxi(res["wins"][0], res["wins"][1]), mini(res["wins"][0], res["wins"][1])]})
	if mine:
		notes.append("%s %s" % [name, "승리!" if res["winner"] == team_id else "탈락"])
		if res["round"] == "ks" and res["winner"] == team_id:
			Milestones.fire_event(career, self, "ks_champion")
	if league.post_over():
		var champ: ProTeam = league.team(league.champions[-1]["team"])
		league.week_events.append({"kind": "champion", "team": champ.name})
		notes.append("%s 한국시리즈 우승" % champ.name)
		phase = PHASE_DONE


## 포스트시즌 이번 라운드에서 내가 친 기록 (지난주까지 post 기록과의 차이)
var _post_seen := PlayerData.SeasonStats.new()


func _post_delta(p: ProPlayer) -> PlayerData.SeasonStats:
	var d := PlayerData.SeasonStats.new()
	d.games = p.post.games - _post_seen.games
	d.pa = p.post.pa - _post_seen.pa
	d.ab = p.post.ab - _post_seen.ab
	d.hits = p.post.hits - _post_seen.hits
	d.doubles = p.post.doubles - _post_seen.doubles
	d.triples = p.post.triples - _post_seen.triples
	d.home_runs = p.post.home_runs - _post_seen.home_runs
	d.rbi = p.post.rbi - _post_seen.rbi
	d.walks = p.post.walks - _post_seen.walks
	d.strikeouts = p.post.strikeouts - _post_seen.strikeouts
	_post_seen = PlayerData.SeasonStats.new()
	_post_seen.merge(p.post)
	return d


func _row_from(res: Dictionary, direct: bool) -> Dictionary:
	var home: bool = res["home"] == team_id
	var opp: ProTeam = league.team(res["away"] if home else res["home"])
	var my_score: int = res["score"][1] if home else res["score"][0]
	var their: int = res["score"][0] if home else res["score"][1]
	return _game_row(opp.name, home, my_score, their, res["lines"].get(me.id, null), direct, level)


func _game_row(opp: String, home: bool, my: int, their: int, line: PlayerData.SeasonStats, direct: bool, lv: String) -> Dictionary:
	return {"opp": opp, "home": home, "my": my, "their": their, "result": signi(my - their), "line": line, "direct": direct, "level": lv}


func _mark_debut(career: CareerState, notes: Array[String]) -> void:
	if debuted:
		return
	debuted = true
	career.player.flags["pro_debut"] = true
	notes.append("1군 데뷔!")
	Milestones.fire_event(career, self, "debut")


func _mark_post_debut(career: CareerState) -> void:
	if not career.player.flags.has("pro_post_debut"):
		career.player.flags["pro_post_debut"] = true
		Milestones.fire_event(career, self, "post_debut")


## 팀 연승·연패 소식
func _week_team_events() -> void:
	for t: ProTeam in league.teams:
		if absi(t.streak) >= 5 and absi(t.streak) % 5 == 0:
			league.week_events.append({"kind": "streak_win" if t.streak > 0 else "streak_loss", "team": t.name, "n": absi(t.streak)})


# ---------- 1군·2군 ----------

## 감독 신뢰 (매주): 이번 주 OPS 가 기준보다 얼마나 높나 + 홈런 − 실책 + 훈련 성실. 복무 중엔 안 바뀐다.
## 1군은 1군 기준(pivot·scale), 퓨처스는 퓨처스 기준(futuresPivot·futuresScale)
func _update_trust(career: CareerState, line: PlayerData.SeasonStats, games: int) -> void:
	if level == LEVEL_MILITARY or games == 0:
		return
	var tc: Dictionary = career.cfg.pro["trust"]
	var d := 0.0
	if line.pa >= int(tc["minPa"]):
		var pivot := float(tc["pivot"] if is_first() else tc["futuresPivot"])
		var scale := float(tc["scale"] if is_first() else tc["futuresScale"])
		d = clampf((line.obp() + line.slg() - pivot) * scale, -float(tc["maxWeekly"]), float(tc["maxWeekly"]))
	d += line.home_runs * float(tc["perHomeRun"]) - line.errors * float(tc["perError"])
	if career._week_training_id != "":
		d += float(tc["workBonus"])
	add_trust(career, d)


## 감독 신뢰를 바꾼다 (주간 변화 · 이야기 효과 proTrust)
func add_trust(career: CareerState, d: float) -> void:
	trust = clampf(trust + d, 0.0, 100.0)
	career.player.vars["pro_trust"] = int(trust)


## 콜업·강등 (정규시즌만) — 감독 신뢰로만 정한다.
## 1군: 신뢰가 demoteBelow 아래인 채 watchGames 경기를 지켜보면 퓨처스로 (신뢰는 demotedSet).
## 퓨처스: 신뢰가 callupAbove 이상인 채 callupWatchGames 경기를 지켜보면 1군으로. 기준을 다시 넘으면 지켜보던 경기 수는 0.
## 다쳐서 경기가 없던 주는 세지도 지우지도 않는다. 1군에서는 OPS 순위로 타순을 정한다
func _check_level(career: CareerState, games: int, notes: Array[String]) -> void:
	if phase != PHASE_REGULAR or level == LEVEL_MILITARY:
		return
	var cfg := career.cfg
	var tc: Dictionary = cfg.pro["trust"]
	var p := career.player
	if not is_first():
		if trust >= float(tc["callupAbove"]):
			watch_games += games
		elif games > 0:
			watch_games = 0
		if watch_games >= int(tc["callupWatchGames"]):
			level = LEVEL_FIRST
			watch_games = 0
			p.lineup_slot = int(cfg.pro["debutSlot"])
			p.flags["pro_called_up"] = true
			notes.append("1군 콜업! (감독 신뢰 %d)" % int(trust))
			league.week_events.append({"kind": "callup"})
			Milestones.fire_event(career, self, "callup")
		return
	if trust < float(tc["demoteBelow"]):
		watch_games += games
	elif games > 0:
		watch_games = 0
	if watch_games >= int(tc["watchGames"]):
		level = LEVEL_FUTURES
		watch_games = 0
		futures_weeks = 0
		trust = float(tc["demotedSet"])
		p.vars["pro_trust"] = int(trust)
		notes.append("퓨처스(2군)로 내려갔다 — 감독이 %d경기를 지켜봤다" % int(tc["watchGames"]))
		league.week_events.append({"kind": "demote"})
		return
	# 타순: 팀 안 OPS 순위 (minPa 넘으면)
	var s := me.season
	if s.pa >= int(cfg.pro["lineupMinPa"]):
		var my_ops := s.obp() + s.slg()
		var better := 0
		for q: ProPlayer in team().lineup:
			if q.season.pa >= int(cfg.pro["lineupMinPa"]) and q.season.obp() + q.season.slg() > my_ops:
				better += 1
		var by_rank: Array = cfg.pro["slotByOpsRank"]
		p.lineup_slot = int(by_rank[mini(better, by_rank.size() - 1)])


# ---------- 시즌 결산 ----------

## 시상·연봉 협상 (시즌 결산 화면이 한 번 부른다 — 다시 불러도 같은 결과)
func close_season(career: CareerState) -> Array:
	if _closed:
		return season_awards
	_closed = true
	var cfg := career.cfg
	var all := league.give_awards(cfg)
	for a: Dictionary in all:
		if a["id"] == me.id:
			season_awards.append(a["award"])
			Milestones.fire_award(career, self, a["award"])
			fame = mini(100, fame + int(cfg.pro["famePerAward"]))
		league.week_events.append({"kind": "award", "award": a["award"], "name": a["name"], "team": a["team"]})
	var v := me.value(league.league_ops(), league.league_ra9())
	value_history.append(v)
	last_value_rank = ProNational.my_rank(self, false)
	# 병역 카드 조건 (다음 시즌 나이로): 물을 나이인가 · 마지막 기회인가
	var mc: Dictionary = cfg.pro["military"]
	career.player.vars["military_ask"] = military == ProService.NONE and me.age + 1 >= int(mc["askAge"])
	career.player.vars["military_last"] = me.age + 1 >= int(mc["lastAge"])
	# FA: 등록 시즌·계약 해 → FA 가 되면 연봉은 제안으로 정한다 (시즌 결산 화면에서 고른다)
	var under_contract := contract_years_left > 0
	ProFA.close_season(cfg, self)
	if fa_pending:
		season_note = "FA 자격! (등록 %d시즌) — 구단 제안을 고른다" % fa_seasons
		feed.after_season(career, self)
		return season_awards
	if under_contract:
		season_note = "FA 계약 %d년 남음 · 연봉 %s" % [contract_years_left, money_text(salary)]
		feed.after_season(career, self)
		return season_awards
	# 연봉: 기여도 구간별 인상률 (1군 타석이 적으면 그만큼만)
	var rate := 0.0
	for row: Array in cfg.pro["salary"]["raiseByValue"]:
		if v >= float(row[0]):
			rate = float(row[1])
			break
	var share := clampf(float(me.season.pa) / float(cfg.pro["salary"]["fullPa"]), 0.0, 1.0)
	var next := maxi(int(cfg.pro["salary"]["min"]), roundi(salary * (1.0 + rate * share) / 1000000.0) * 1000000)
	for a: String in season_awards:
		next += int(cfg.pro["salary"]["awardBonus"].get(a, 0))
	season_note = "내년 연봉 %s (%+d%%)" % [money_text(next), roundi((float(next) / maxi(1, salary) - 1.0) * 100.0)]
	salary = next
	salary_history.append({"year": league.year + 1, "salary": salary})
	feed.after_season(career, self)
	return season_awards


## 다음 시즌으로 (결산 뒤)
func start_next_season(career: CareerState) -> void:
	league.next_season(career.cfg)
	career.player.season = me.season
	futures_season = PlayerData.SeasonStats.new()
	futures_weeks = 0
	_post_seen = PlayerData.SeasonStats.new()
	phase = PHASE_REGULAR
	season_awards = []
	season_note = ""
	_closed = false
	week_games = []
	first_weeks = 0
	watch_games = 0
	# 1군에 있던 선수는 1군에서 시작한다 (스프링캠프는 이야기 카드로). 복무 중이면 그대로
	if level == LEVEL_FUTURES and debuted and trust >= float(career.cfg.pro["trust"]["callupAbove"]):
		level = LEVEL_FIRST
	career.player.vars["pro_age"] = me.age
	ProService.season_start(career, self, start_notes)


## "3억 2천만원", "3,000만원"
static func money_text(won: int) -> String:
	var eok := won / 100000000
	var man := (won % 100000000) / 10000
	if eok > 0 and man > 0:
		return "%d억 %d만원" % [eok, man]
	if eok > 0:
		return "%d억원" % eok
	return "%d만원" % man
