class_name ProCareer
extends RefCounted
## 내 프로 생활: 드래프트 지명 → 계약 → 2군(퓨처스)·1군 오르내림 → 정규시즌 → 포스트시즌 → 시상·연봉 → 다음 시즌.
## 리그(ProLeague)의 한 선수(me)로 들어가서, 내 기록도 리그의 다른 선수와 같은 방식(박스스코어)으로 쌓인다.
##
## 달력 (GameCalendar): 한 주 = 월요일 휴식(훈련·이야기) + 화~일 6경기. 경기는 날마다 하나씩 — **모든 경기를 직접 하거나 시뮬레이션**한다
##   (내가 1군이면 1군 경기, 퓨처스면 퓨처스 경기, 상무면 상무 경기). 시뮬레이션해도 내 선수는 능력치대로 나와 친다.
##   "날짜까지 시뮬레이션"(sim_until)은 중요한 일(이야기·기념 순간·1군↔퓨처스·부상·시즌 끝)이 생기면 그 자리에서 멈춘다.
##   그날 리그의 다른 경기도 모두 같이 돌린다. 주의 마지막 날 뒤에 성장·컨디션·부상·폼, 감독 신뢰, 연봉(주급), 국가대표(ProNational),
##   복무(ProService), 뉴스·SNS. 기념 순간(Milestones)은 경기마다. 시즌 결산에서 시상·연봉·FA(ProFA)·입대 카드.
## 1군·퓨처스는 감독 신뢰(trust)로만 오르내린다: 신뢰는 한 주마다 바뀌고, 기준 아래(1군)·위(퓨처스)인 채 정해진 경기 수를
##   지켜보면(경기마다 센다) 그 경기 뒤에 움직인다.
## 포스트시즌: 라운드마다 한 주, 하루 한 경기 — 내 팀 경기는 직접 하거나 시뮬레이션, 다른 팀 시리즈는 시뮬레이션.
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
## 이번 주 경기들 [{"day", "team", "mine", "line": 내 SeasonStats | null, "post"?, "other"?}] (허브 목록)
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
## FA 등록 시즌 · 남은 계약 해(FA 계약) · FA 결정 대기 · FA 횟수 · 메인 엔딩("" | "stay" | "move")
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
## 달력: 프로 1년 차의 달력 해 · 이번 주 몇 번째 경기 날인가(0~5, 포스트시즌은 라운드 안 경기 수) · 마지막으로 경기한 날
var base_year := 2030
var day := 0
var last_day := 0
## 이번 주 내 경기 줄 (한 주 정리에서 성장·감독 신뢰에 쓴다)
var week_lines: Array = []
## 경기 기록 (달력): 날짜 → {"team": 내 팀 경기 {"opp", "home", "my", "their", "result"}, "mine": 내 경기 {"level", "line", "direct", …}, "post"?, "other"?}
var game_log := {}
## 이번 시즌 1군에 있던 경기 날 (FA 등록 시즌 — faWeeks × gamesPerWeek 이상)
var first_days := 0
## 나이 곡선 한 해 변화의 소수 부분 (능력치 → 쌓인 값)
var age_progress := {}
## 직접 할 경기 (new_game 과 finish_day 사이에만 있다 — 저장하지 않는다)
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
	pro.base_year = GameCalendar.hs_year(cfg, 3) + 1 + (int(pc["college"]["years"]) if draft.get("college", false) else 0)
	pro.me = ProPlayer.make_batter("%s:me" % t.id, p.name, t.id, "유격수", p.skills_for_game(cfg), age, 1)
	pro.me.is_me = true
	t.bench.push_front(pro.me)
	pro.level = LEVEL_FIRST if pro.tier in pc["startFirst"] else LEVEL_FUTURES
	pro.trust = float(pc["trust"]["startByTier"].get(pro.tier, 45))
	pro.college = bool(draft.get("college", false))
	p.vars["pro_age"] = age
	p.age = age
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


# ---------- 달력 · 오늘 경기 ----------

## 이 시즌의 달력 해 (프로 1년 차 = 고교 3학년 다음 해, 대학이면 4년 더 뒤)
func cal_year() -> int:
	return base_year + league.year - 1


## 지금 주가 달력에서 몇째 주인가 (정규시즌 week_no, 포스트시즌은 정규시즌 다음 주부터 라운드마다 한 주)
func cal_week(cfg: CareerConfig) -> int:
	if phase == PHASE_REGULAR:
		return week_no()
	return int(cfg.pro["weeks"]) + 1 + league.post_round


## 오늘: 주 첫날 훈련 전이면 그 주 월요일(휴식일 — 훈련·이야기), 아니면 다음에 할 경기 날.
## 이번 주 경기를 다 했거나 시즌이 끝났으면 마지막 경기 날
func today(career: CareerState) -> int:
	var cfg := career.cfg
	if career.game_done or phase == PHASE_DONE:
		return last_day if last_day > 0 else GameCalendar.pro_opening(cfg, cal_year())
	if is_off_day(career):
		return GameCalendar.pro_monday(cfg, cal_year(), cal_week(cfg))
	return GameCalendar.pro_day(cfg, cal_year(), cal_week(cfg), day)


## 오늘이 월요일 휴식일인가 (이번 주 경기 전, 아직 훈련을 안 했다 — 더쇼 RTTS 의 쉬는 날처럼 훈련을 고르는 날)
func is_off_day(career: CareerState) -> bool:
	return phase != PHASE_DONE and not career.game_done and day == 0 and career.week_training == ""


## 다음 주 월요일 (이번 주 경기를 다 한 뒤 — 시뮬레이션이 다음 주로 넘어가도 되는지 본다)
func next_monday(career: CareerState) -> int:
	if phase == PHASE_DONE:
		return 1 << 30
	return GameCalendar.pro_monday(career.cfg, cal_year(), cal_week(career.cfg))


## 오늘 내 경기 {"day": 날짜, "kind", "opp": ProTeam | null, "home", "playable": 직접 할 수 있나, "text"}.
## kind: "first" 1군 · "futures" 퓨처스 · "sangmu" 상무 · "bench" (내 팀 경기에 못 나간다 — 부상, 퓨처스인데 포스트시즌)
##       · "none" (내 경기가 없다 — 현역 복무, 우리 팀이 없는 포스트시즌 라운드, 이번 주 경기를 다 했다)
func game_today(career: CareerState) -> Dictionary:
	var cfg := career.cfg
	var out := {"day": today(career), "kind": "none", "opp": null, "home": false, "playable": false, "text": "", "pitcher": null}
	if phase == PHASE_DONE:
		out["text"] = "시즌이 끝났다"
		return out
	if career.game_done:
		out["text"] = "이번 주 경기를 다 했다"
		return out
	var hurt := career.player.is_injured()
	if phase == PHASE_POST:
		if league.post_over():
			out["text"] = "포스트시즌이 끝났다"
			return out
		var st := league.post_begin(cfg)
		var name: String = cfg.pro["postseason"][st["round"]]["name"]
		var g := int(st["g"])
		var home_id: String = st["hi"] if g in [0, 1, 4, 6] else st["lo"]
		if team_id == st["hi"] or team_id == st["lo"]:
			out["opp"] = league.team(st["lo"] if team_id == st["hi"] else st["hi"])
			out["home"] = home_id == team_id
			out["kind"] = "first" if is_first() and not hurt else "bench"
			out["text"] = "%s %d차전 vs %s" % [name, g + 1, (out["opp"] as ProTeam).name]
			var ot: ProTeam = out["opp"]
			out["pitcher"] = ot.starters[ot.rotation % ot.starters.size()]
		else:
			var away_id: String = st["lo"] if home_id == st["hi"] else st["hi"]
			out["text"] = "%s %d차전 %s vs %s (우리 팀 없음)" % [name, g + 1, league.team(away_id).name, league.team(home_id).name]
		out["playable"] = out["kind"] == "first"
		return out
	var tg := league.team_game_on(cfg, team_id, week_no(), day)
	match level:
		LEVEL_FIRST:
			out["opp"] = league.team(tg["opponent"])
			out["home"] = tg["is_home"]
			out["kind"] = "bench" if hurt else "first"
		LEVEL_FUTURES:
			out["opp"] = _minor_opp(day)
			out["home"] = (week_no() + day) % 2 == 0
			out["kind"] = "bench" if hurt else "futures"
		LEVEL_MILITARY:
			if military == ProService.SANGMU and not hurt:
				out["opp"] = _minor_opp(day)
				out["home"] = (week_no() + day) % 2 == 0
				out["kind"] = "sangmu"
	out["playable"] = out["kind"] in ["first", "futures", "sangmu"]
	# 상대 예고 선발 (1군 경기만 — 로테이션 차례)
	if out["kind"] in ["first", "bench"] and out["opp"] != null:
		var ot: ProTeam = out["opp"]
		out["pitcher"] = ot.starters[ot.rotation % ot.starters.size()]
	var where: String = {"first": "1군", "futures": "퓨처스", "sangmu": "상무", "bench": "결장 — 우리 팀 경기", "none": "경기 없음"}[out["kind"]]
	if out["opp"] != null:
		out["text"] = "%s · vs %s (%s)" % [where, (out["opp"] as ProTeam).name, "홈" if out["home"] else "원정"]
	else:
		out["text"] = where
	return out


## 이번 주 내가 직접 할 경기가 있나 (오늘 경기 — 예전 이름)
func has_direct_game(career: CareerState) -> bool:
	return game_today(career)["playable"]


## 오늘 상대 (화면용, 없으면 null)
func next_opponent(career: CareerState) -> ProTeam:
	return game_today(career)["opp"]


## 퓨처스·상무 경기 상대 (내 구단을 빼고 날마다 돌아가며)
func _minor_opp(d: int) -> ProTeam:
	var others := league.teams.filter(func(t: ProTeam) -> bool: return t.id != team_id)
	return others[(week_no() * 6 + d) % others.size()]


## 퓨처스·상무 경기 하나 (direct: 내가 직접 — 아니면 내 자리에 리그 속 내 타자를 넣어 자동으로)
func _minor_game(career: CareerState, gt: Dictionary, direct: bool) -> GameRunner:
	var mine: School = ProService.sangmu_school(career, self) if gt["kind"] == "sangmu" else team().futures
	var g := GameRunner.new(career.cfg, career.batting, career.player, mine, (gt["opp"] as ProTeam).futures, gt["home"],
		hash([league.seed, league.year, cal_week(career.cfg), day, gt["kind"]]), direct, false)
	if not direct:
		g.teams[g.my_side].lineup[career.player.lineup_slot - 1] = me.batter
	return g


## 오늘 경기를 직접 하려고 만든다 (GameScreen 이 CareerState.new_game 으로 부른다)
func new_game(career: CareerState) -> GameRunner:
	var cfg := career.cfg
	auto_training(career)
	var gt := game_today(career)
	assert(gt["playable"], "오늘은 직접 할 경기가 없다")
	_sync_me(career)
	if gt["kind"] == "first":
		if phase == PHASE_POST:
			_pending = league.post_next_match(cfg, career.batting, career.player, me, team_id, true)
		else:
			var tg := league.team_game_on(cfg, team_id, week_no(), day)
			_pending = league.make_game(cfg, career.batting, league.team(tg["away"]), league.team(tg["home"]), tg["salt"],
				career.player, me, team_id, true)
	else:
		_pending = {"game": _minor_game(career, gt, true)}
	_pending["kind"] = gt["kind"]
	_pending["day"] = gt["day"]
	return _pending["game"]


## 내 능력치를 리그 명단 속 내 타자에 맞춘다 (컨디션·폼·장비까지 — 자동 경기에서도 지금 몸 상태로 친다)
func _sync_me(career: CareerState) -> void:
	me.batter.skills = career.player.skills_for_game(career.cfg)
	me.name = career.player.name
	me.batter.name = career.player.name


# ---------- 하루 · 한 주 ----------

## 오늘 하루: 내 경기(game = 내가 직접 한 경기, null 이면 시뮬레이션) + 그날 리그의 다른 경기 전부.
## 이번 주 마지막 경기 날이면 한 주 정리(성장·감독 신뢰·주급·국가대표·복무·뉴스)까지 하고 career.game_done 을 켠다.
## 돌려주는 값: 변화 설명
func finish_day(career: CareerState, game: GameRunner = null) -> Array[String]:
	var notes: Array[String] = []
	if career.game_done or phase == PHASE_DONE:
		_pending = {}
		return notes
	auto_training(career)
	_sync_me(career)
	var direct := game != null and not _pending.is_empty() and int(_pending.get("day", -1)) == today(career)
	if phase == PHASE_POST:
		_post_day(career, game if direct else null, notes)
	else:
		_regular_day(career, game if direct else null, notes)
	_pending = {}
	return notes


## 이번 주 남은 날을 모두 (첫날은 game 이 있으면 그 경기) — 자동 진행·테스트용
func finish_week(career: CareerState, game: GameRunner = null) -> Array[String]:
	var notes: Array[String] = []
	if game != null:
		notes.append_array(finish_day(career, game))
	var guard := 0
	while not career.game_done and phase != PHASE_DONE and guard < 10:
		guard += 1
		notes.append_array(finish_day(career, null))
	return notes


## 다음 주 (CareerState.advance_week 가 부른다)
func new_week() -> void:
	day = 0
	week_lines = []
	week_games = []


func _regular_day(career: CareerState, game: GameRunner, notes: Array[String]) -> void:
	var cfg := career.cfg
	var bc := career.batting
	var wk := week_no()
	var d := day
	var gt := game_today(career)
	var date: int = gt["day"]
	var tg := league.team_game_on(cfg, team_id, wk, d)
	var me_in: bool = gt["kind"] == "first"
	var direct := game != null
	# 내 팀 1군 경기 (내가 1군이고 건강하면 나도 나온다 — 직접 했으면 그 경기)
	var m: Dictionary
	if me_in and direct:
		m = _pending
	else:
		m = league.make_game(cfg, bc, league.team(tg["away"]), league.team(tg["home"]), tg["salt"],
			career.player if me_in else null, me if me_in else null, team_id if me_in else "", false)
		(m["game"] as GameRunner).run_to_end()
	var res := league.record_game(m, "", game.my_line if me_in and direct else null)
	var entry := {"team": _team_row(res)}
	var my_line: PlayerData.SeasonStats = null
	if me_in:
		if direct:
			me.season.games += 1  # 박스스코어 줄(from_box)은 games 를 1로 넣지만 my_line 은 0이다
		my_line = res["lines"][me.id]
		first_days += 1
		_mark_debut(career, notes)
		entry["mine"] = {"level": LEVEL_FIRST, "line": my_line.line_text(), "direct": direct}
	# 퓨처스·상무 경기 (내 기록만 센다)
	elif gt["kind"] in ["futures", "sangmu"]:
		var fg: GameRunner = game if direct else _minor_game(career, gt, false)
		if not direct:
			fg.run_to_end()
		my_line = _minor_line(career, fg, direct)
		(service_line if gt["kind"] == "sangmu" else futures_season).merge(my_line)
		var r := signi(fg.state.score[fg.my_side] - fg.state.score[1 - fg.my_side])
		entry["mine"] = {"level": gt["kind"], "line": my_line.line_text(), "direct": direct, "opp": (gt["opp"] as ProTeam).short,
			"score": "%d:%d" % [fg.state.score[fg.my_side], fg.state.score[1 - fg.my_side]], "result": r}
	# 그날 다른 팀 경기
	league.sim_day_others(cfg, bc, wk, d, team_id)
	game_log[date] = entry
	last_day = date
	week_games.append(_week_row(date, entry, my_line))
	if my_line != null:
		week_lines.append(my_line)
		Milestones.check(career, self, [my_line])
		_check_level(career, true, notes)
	day += 1
	if day >= int(cfg.pro["gamesPerWeek"]):
		_end_regular_week(career, notes)


## 퓨처스·상무 경기의 내 줄 (경기 수 1)
func _minor_line(career: CareerState, fg: GameRunner, direct: bool) -> PlayerData.SeasonStats:
	if direct:
		var line := PlayerData.SeasonStats.new()
		line.merge(fg.my_line)
		line.games = 1
		return line
	return PlayerData.SeasonStats.from_box(fg.box.batters[fg.my_side][career.player.lineup_slot - 1])


## 정규시즌 한 주의 마지막 날 뒤: 복무 · 리그 주차 · 국가대표 · 한 주 정리 · 시즌이 끝났으면 포스트시즌으로
func _end_regular_week(career: CareerState, notes: Array[String]) -> void:
	var cfg := career.cfg
	if level == LEVEL_FUTURES:
		futures_weeks += 1
	ProService.week(career, self, notes)
	league.weeks_done += 1
	_week_team_events()
	ProNational.week(career, self, notes)
	_weekly(career, notes)
	if league.season_over(cfg):
		league.lock_standings()
		phase = PHASE_POST
		var t := team()
		var rank := league.rank_of(team_id)
		notes.append("정규시즌 %d위로 마감 (%d승 %d패 %d무)" % [rank, t.w, t.l, t.d])
		if rank > int(cfg.pro["postseasonTeams"]):
			notes.append("가을야구 진출 실패")
	career.game_done = true


func _post_day(career: CareerState, game: GameRunner, notes: Array[String]) -> void:
	var cfg := career.cfg
	if league.post_over():
		phase = PHASE_DONE
		career.game_done = true
		return
	var gt := game_today(career)
	var date: int = gt["day"]
	var me_in: bool = gt["kind"] == "first"
	var direct := game != null and me_in
	var m: Dictionary
	if direct:
		m = _pending
	else:
		m = league.post_next_match(cfg, career.batting, career.player if me_in else null, me if me_in else null, team_id if me_in else "", false)
		(m["game"] as GameRunner).run_to_end()
	if me_in:
		_mark_post_debut(career)
	var round: String = league.post_state["round"]
	var res := league.post_record(cfg, m, game.my_line if direct else null)
	var entry := {"team": _team_row(res), "post": cfg.pro["postseason"][round]["name"]}
	var my_line: PlayerData.SeasonStats = null
	if me_in:
		if direct:
			me.post.games += 1
		my_line = res["lines"][me.id]
		entry["mine"] = {"level": LEVEL_FIRST, "line": my_line.line_text(), "direct": direct}
		week_lines.append(my_line)
		Milestones.check(career, self, [my_line])
	if (entry["team"] as Dictionary).is_empty():
		entry["other"] = "%s %d:%d %s" % [league.team(res["away"]).short, res["score"][0], res["score"][1], league.team(res["home"]).short]
	game_log[date] = entry
	last_day = date
	week_games.append(_week_row(date, entry, my_line))
	day += 1
	if not res["decided"]:
		return
	var r: Dictionary = res["result"]
	var name: String = cfg.pro["postseason"][r["round"]]["name"]
	var w: ProTeam = league.team(r["winner"])
	var loser: ProTeam = league.team(r["lo"] if r["winner"] == r["hi"] else r["hi"])
	league.week_events.append({"kind": "post", "round": name, "winner": w.name, "loser": loser.name,
		"wins": "%d승 %d패" % [maxi(r["wins"][0], r["wins"][1]), mini(r["wins"][0], r["wins"][1])]})
	if team_id == r["hi"] or team_id == r["lo"]:
		notes.append("%s %s" % [name, "승리!" if r["winner"] == team_id else "탈락"])
		if r["round"] == "ks" and r["winner"] == team_id:
			Milestones.fire_event(career, self, "ks_champion")
	if league.post_over():
		var champ: ProTeam = league.team(league.champions[-1]["team"])
		league.week_events.append({"kind": "champion", "team": champ.name})
		notes.append("%s 한국시리즈 우승" % champ.name)
		phase = PHASE_DONE
	_weekly(career, notes)
	career.game_done = true


## 한 주 정리 (정규시즌 주 · 포스트시즌 라운드): 이번 주 내 기록으로 성장·폼·컨디션·인기, 부상, 주급, 감독 신뢰, 타순, 뉴스·SNS
func _weekly(career: CareerState, notes: Array[String]) -> void:
	var cfg := career.cfg
	var week_line := PlayerData.SeasonStats.new()
	for ln: PlayerData.SeasonStats in week_lines:
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
	# 주급 (연봉 ÷ 정규시즌 주 수) — 복무 중엔 구단 연봉이 없다
	if phase == PHASE_REGULAR and level != LEVEL_MILITARY:
		var pay := salary / int(cfg.pro["weeks"])
		career.player.money += pay
		career.last_income = [["주급", pay]]
	_update_trust(career, week_line, week_lines.size())
	_check_level(career, false, notes)
	_set_lineup_slot(career)
	feed.after_week(career, self, week_lines)
	league.week_events = []
	var r := 0
	for g: Dictionary in week_games:
		r += int((g.get("team", {}) as Dictionary).get("result", 0))
	career.last = {"result": "win" if r > 0 else ("loss" if r < 0 else "draw"), "hits": week_line.hits, "home_runs": week_line.home_runs,
		"phase_end": "", "national": false, "opponent_id": "", "season_over": phase == PHASE_DONE}


## 내 팀 경기 한 줄 (그 경기에 내 팀이 없으면 {}): {"opp": 약칭, "home", "my", "their", "result"}
func _team_row(res: Dictionary) -> Dictionary:
	if res["home"] != team_id and res["away"] != team_id:
		return {}
	var home: bool = res["home"] == team_id
	var my_score: int = res["score"][1] if home else res["score"][0]
	var their: int = res["score"][0] if home else res["score"][1]
	return {"opp": league.team(res["away"] if home else res["home"]).short, "home": home, "my": my_score, "their": their,
		"result": signi(my_score - their)}


## 이번 주 경기 목록 한 줄 (허브): {"day", "team": 내 팀 경기, "mine": 내 경기, "line": 내 기록, "other"}
func _week_row(date: int, entry: Dictionary, line: PlayerData.SeasonStats) -> Dictionary:
	var row := entry.duplicate()
	row["day"] = date
	row["line"] = line
	return row


# ---------- 날짜까지 시뮬레이션 ----------

const STOP_TEXT := {"date": "정한 날짜에 도착했다", "story": "이야기가 생겨 멈췄다", "milestone": "기념할 순간이 생겨 멈췄다",
	"level": "1군·퓨처스가 바뀌어 멈췄다", "injury": "부상으로 멈췄다", "phase": "정규시즌이 끝나 멈췄다", "season_end": "시즌이 끝났다",
	"minigame": "할 일이 생겨 멈췄다", "playable": "다시 경기에 나갈 수 있다", "guard": "멈췄다"}
## 시뮬레이션을 멈출 수 있는 일 (끄고 켤 수 있는 것 — 더쇼의 시뮬레이션 중단 설정). 이야기·날짜·시즌 끝에서는 늘 멈춘다
const STOPS := ["milestone", "level", "injury"]


## target 날짜(그날 경기 전)까지 시뮬레이션 — 더쇼 RTTS 의 "날짜까지 시뮬레이션". target 당일 경기는 하지 않는다 (그날은 직접 할 수 있다).
## 주가 바뀌면 월요일 훈련은 지난주 계획 그대로(없으면 휴식). 이야기 카드(주 시작·경기 뒤)·시즌 끝·정규시즌 끝에서는 늘 멈추고,
## stops 에서 켠 일(기념 순간 milestone · 1군↔퓨처스·입대·전역 level · 새 부상 injury)이 생겨도 멈춘다.
## until_playable: 내가 직접 할 수 있는 경기가 오면 그 경기 전에 멈춘다 ("다음 출전까지" — 부상·현역·우리 팀이 없는 가을야구 동안).
## 돌려주는 값: {"stop": 이유, "text", "notes", "days": 진행한 날 수}
func sim_until(career: CareerState, target: int, stops: Dictionary = {}, until_playable: bool = false) -> Dictionary:
	var notes: Array[String] = []
	var days := 0
	var stop_on := func(k: String) -> bool: return bool(stops.get(k, true))
	for guard in 1000:
		if phase == PHASE_DONE and career.game_done:
			return _stop("season_end", notes, days)
		if career.game_done:
			if not career.events("after").is_empty():
				return _stop("story", notes, days)
			if next_monday(career) > target:
				return _stop("date", notes, days)
			career.advance_week()
			if not career.events("start").is_empty():
				return _stop("story", notes, days)
			continue
		if career.pending_minigame != "":
			return _stop("minigame", notes, days)
		# 주 첫날 이야기가 아직 남아 있으면 (허브가 띄우기 전) 먼저
		if day == 0 and not career.events("start").is_empty():
			return _stop("story", notes, days)
		if today(career) >= target:
			return _stop("date", notes, days)
		auto_training(career)
		if today(career) >= target:
			return _stop("date", notes, days)
		if until_playable and game_today(career)["playable"]:
			return _stop("playable", notes, days)
		var lv := level
		var mil := military
		var hurt := career.player.is_injured()
		var scenes := pending_scenes.size()
		var ph := phase
		notes.append_array(finish_day(career, null))
		days += 1
		if pending_scenes.size() > scenes and stop_on.call("milestone"):
			return _stop("milestone", notes, days)
		if (level != lv or military != mil) and stop_on.call("level"):
			return _stop("level", notes, days)
		if career.player.is_injured() and not hurt and stop_on.call("injury"):
			return _stop("injury", notes, days)
		if phase != ph and phase != PHASE_DONE:
			return _stop("phase", notes, days)
	return _stop("guard", notes, days)


## 이번 주 훈련을 아직 안 했으면 지난주 계획 그대로 (계획이 비었으면 휴식) — 시뮬레이션·자동 진행용
func auto_training(career: CareerState) -> void:
	if career.week_training != "":
		return
	if career.plan_ready():
		career.run_schedule()
	else:
		career.train("rest")


func _stop(reason: String, notes: Array[String], days: int) -> Dictionary:
	return {"stop": reason, "text": STOP_TEXT.get(reason, ""), "notes": notes, "days": days}


# ---------- 달력 화면용 ----------

## y 년 m 월 달력 칸 {날짜: {"title", "sub", "state", "pick", "me"}} — state: win | loss | draw | future | off | today | other.
## 지난 경기는 기록(game_log)에서, 이번 시즌 남은 정규시즌 경기는 일정에서 (상대 · 홈/원정). pick: 그 날짜까지 시뮬레이션할 수 있나
func calendar_cells(career: CareerState, y: int, m: int) -> Dictionary:
	var cfg := career.cfg
	var first := GameCalendar.days_from_civil(y, m, 1)
	var last := first + GameCalendar.days_in_month(y, m) - 1
	var now := today(career)
	var cells := {}
	# 이번 시즌 남은 정규시즌 일정 (월요일은 휴식·훈련일)
	if phase == PHASE_REGULAR:
		for wk in range(week_no(), int(cfg.pro["weeks"]) + 1):
			var mon := GameCalendar.pro_monday(cfg, cal_year(), wk)
			if mon >= first and mon <= last and mon >= now:
				cells[mon] = {"title": "휴식", "sub": "훈련", "state": "off", "pick": true, "me": ""}
			for d in int(cfg.pro["gamesPerWeek"]):
				var z := GameCalendar.pro_day(cfg, cal_year(), wk, d)
				if z < first or z > last or z < now:
					continue
				var tg := league.team_game_on(cfg, team_id, wk, d)
				cells[z] = {"title": league.team(tg["opponent"]).short, "sub": "홈" if tg["is_home"] else "원정",
					"state": "today" if z == now else "future", "pick": z > now, "me": ""}
	# 지난 경기
	for z: int in game_log:
		if z < first or z > last:
			continue
		var e: Dictionary = game_log[z]
		var t: Dictionary = e.get("team", {})
		var cell := {"title": "", "sub": "", "state": "other", "pick": false, "me": ""}
		if not t.is_empty():
			cell["title"] = t["opp"]
			cell["sub"] = "%d:%d %s" % [t["my"], t["their"], ["패", "무", "승"][int(t["result"]) + 1]]
			cell["state"] = ["loss", "draw", "win"][int(t["result"]) + 1]
		else:
			cell["title"] = "PS"
			cell["sub"] = e.get("other", "")
		var mine: Dictionary = e.get("mine", {})
		if not mine.is_empty():
			cell["me"] = ("★ " if mine.get("direct", false) else "") + ("" if mine["level"] == LEVEL_FIRST else ("2군 " if mine["level"] == LEVEL_FUTURES else "상무 ")) + str(mine["line"])
		if e.has("post"):
			cell["sub"] = "%s · %s" % [str(e["post"]).substr(0, 3), cell["sub"]]
		cells[z] = cell
	# 앞으로의 날짜는 (일정이 안 나온 포스트시즌도) 그날까지 시뮬레이션할 수 있다
	for z in range(maxi(first, now + 1), last + 1):
		if not cells.has(z) and phase != PHASE_DONE:
			cells[z] = {"title": "", "sub": "", "state": "future", "pick": true, "me": ""}
	if now >= first and now <= last:
		if not cells.has(now):
			cells[now] = {"title": "오늘", "sub": "", "state": "today", "pick": false, "me": ""}
		elif cells[now]["state"] in ["future", "off"]:
			cells[now]["state"] = "today"
		if is_off_day(career):
			cells[now]["sub"] = "훈련일"
		cells[now]["pick"] = false
	return cells


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


## 콜업·강등 (정규시즌만) — 감독 신뢰로만 정한다. 내 경기가 끝날 때마다(played) · 한 주 정리에서(신뢰가 바뀐 뒤) 부른다.
## 1군: 신뢰가 demoteBelow 아래인 채 watchGames 경기를 지켜보면 그 경기 뒤 퓨처스로 (신뢰는 demotedSet).
## 퓨처스: 신뢰가 callupAbove 이상인 채 callupWatchGames 경기를 지켜보면 1군으로. 신뢰가 기준을 다시 넘으면 지켜보던 경기 수는 0.
## 다쳐서 경기가 없는 날은 세지 않는다 (신뢰도 안 바뀌니 지우지도 않는다)
func _check_level(career: CareerState, played: bool, notes: Array[String]) -> void:
	if phase != PHASE_REGULAR or level == LEVEL_MILITARY:
		return
	var cfg := career.cfg
	var tc: Dictionary = cfg.pro["trust"]
	var p := career.player
	if not is_first():
		if trust >= float(tc["callupAbove"]):
			if played:
				watch_games += 1
		else:
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
		if played:
			watch_games += 1
	else:
		watch_games = 0
	if watch_games >= int(tc["watchGames"]):
		level = LEVEL_FUTURES
		watch_games = 0
		futures_weeks = 0
		trust = float(tc["demotedSet"])
		p.vars["pro_trust"] = int(trust)
		notes.append("퓨처스(2군)로 내려갔다 — 감독이 %d경기를 지켜봤다" % int(tc["watchGames"]))
		league.week_events.append({"kind": "demote"})


## 1군 타순: 팀 안 OPS 순위 (lineupMinPa 타석부터)
func _set_lineup_slot(career: CareerState) -> void:
	var cfg := career.cfg
	var s := me.season
	if not is_first() or s.pa < int(cfg.pro["lineupMinPa"]):
		return
	var my_ops := s.obp() + s.slg()
	var better := 0
	for q: ProPlayer in team().lineup:
		if q.season.pa >= int(cfg.pro["lineupMinPa"]) and q.season.obp() + q.season.slg() > my_ops:
			better += 1
	var by_rank: Array = cfg.pro["slotByOpsRank"]
	career.player.lineup_slot = int(by_rank[mini(better, by_rank.size() - 1)])


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
	phase = PHASE_REGULAR
	season_awards = []
	season_note = ""
	_closed = false
	week_games = []
	week_lines = []
	day = 0
	first_days = 0
	watch_games = 0
	# 1군에 있던 선수는 1군에서 시작한다 (스프링캠프는 이야기 카드로). 복무 중이면 그대로
	if level == LEVEL_FUTURES and debuted and trust >= float(career.cfg.pro["trust"]["callupAbove"]):
		level = LEVEL_FIRST
	career.player.vars["pro_age"] = me.age
	career.player.age = me.age
	_age_me(career)
	ProService.season_start(career, self, start_notes)


## 나이가 한 살 늘 때 내 선수의 한 해 변화 (나이 곡선 — 리그 선수와 같은 표): 오를 때는 잠재력(한계)도 같이 오르고(자연 성장),
## 내려갈 때는 같이 내려간다(노쇠). 소수는 쌓아 두었다가 1이 차면 움직인다. 주력은 떨어지기만 한다 (30세부터 해마다 1 더)
func _age_me(career: CareerState) -> void:
	var cfg := career.cfg
	var p := career.player
	var delta := float(ProLeague.age_row(cfg, me.age)[2])
	var parts := []
	for s: String in PlayerData.STATS:
		var d := delta
		if s == "speed":
			d = minf(0.0, delta) - (1.0 if me.age >= 30 else 0.0)
		age_progress[s] = float(age_progress.get(s, 0.0)) + d
		var n := int(age_progress[s]) if age_progress[s] >= 0.0 else -int(-age_progress[s])
		if n == 0:
			continue
		age_progress[s] -= n
		var before := p.stat(s)
		p.add_stat(s, n, cfg)
		p.potential[s] = clampi(int(p.potential.get(s, p.stat(s))) + n, p.stat(s), cfg.stat_max)
		if p.stat(s) != before:
			parts.append("%s %+d" % [PlayerData.STAT_LABELS[s], p.stat(s) - before])
	if not parts.is_empty():
		start_notes.append("만 %d세 — %s" % [me.age, " · ".join(parts)])
	p.mark_season_start()


## "3억 2천만원", "3,000만원"
static func money_text(won: int) -> String:
	var eok := won / 100000000
	var man := (won % 100000000) / 10000
	if eok > 0 and man > 0:
		return "%d억 %d만원" % [eok, man]
	if eok > 0:
		return "%d억원" % eok
	return "%d만원" % man
