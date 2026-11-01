class_name CareerState
extends RefCounted
## 커리어 진행: 시즌(주말리그·전국대회), 학교 명단, 훈련, 경기 뒤 성장·폼·부상, 돌발 이벤트·스토리,
## 인물 관계, 라이벌, 스카우트 평가, 겨울 캠프·졸업·진로.
##
## 겨울 캠프 (시즌 결산 뒤, 1·2학년): camp.weeks 주 동안 매주 훈련(보너스) + 이벤트, 일부 주차엔 연습경기.
## 캠프 중엔 camp_week 가 1부터 오르고 시즌 주차(week)는 멈춘다. 연습경기는 순위·시즌 기록에 넣지 않는다.
##
## 한 주 = (주 시작 이야기) → 평일 스케줄(칸 3개에 훈련·휴식, 칸마다 대성공·성공·실패) → 주말 경기 → 결과·성장 → 이벤트.
## 스케줄은 지난주 것을 그대로 이어 쓰므로 바꾸고 싶은 칸만 고치면 된다. 이벤트는 한 주에 0~2개 (EventBook).
## 한 시즌 = 주말리그 전반기(권역 학교와 한 번씩) → 전국대회(16강 토너먼트, 지면 탈락) → 주말리그 후반기 → 전국대회.
## 모든 난수는 커리어 시드 + 시즌 + 주차로 만든다 — 같은 선택이면 같은 결과다 (원칙 2).

## 캠프 집중 과제 중 능력치가 아닌 것: 몸 관리 (캠프 동안 컨디션이 덜 깎인다 — career.camp.body)
const CAMP_FOCUS_BODY := "body"
## 소율이 찍는 영상 수 (docs/scenario 2-1): 실전 타석·실전 수비(직접 한 것)마다 +1. content.json vars 에 선언돼 있을 때만 센다
const VIDEO_AT_BAT := "stats.at_bat_video_count"
const VIDEO_FIELDING := "stats.fielding_video_count"

var cfg: CareerConfig
## 타격·타구 물리 수치 (경기 자동 진행에 쓴다)
var batting: BattingConfig
var player: PlayerData
var season_no := 1
## 이번 시즌 몇 번째 경기 주인가 (1부터)
var week := 1
## 이번 시즌 경기 결과 (1 승, 0 무, -1 패)
var results: Array[int] = []
## 커리어 전체 몇 번째 주인가 (1부터, 시즌이 바뀌어도 이어진다 — 지연 결과·재등장 간격용)
var total_week := 1
## 이번 주에 고른 훈련 (경기 결과 화면 설명용)
var last_training := ""
## 이번 주 스케줄 결과 요약 ("" = 아직 안 함). 스케줄은 한 주에 한 번 — 홈 화면을 다시 열어도 유지된다
var week_training := ""
## 평일 스케줄 칸마다 넣은 훈련 id (cfg.schedule.slots 칸 수, "" = 빈 칸). 다음 주에도 그대로 남는다
var week_plan: Array = []
## 이번 주 스케줄 실행 결과 (칸마다): {"slot", "id", "label", "outcome" ("great"|"success"|"fail"|"rest"), "outcome_label",
##   "stat", "amount" (오른 성장량, 소수), "raised" (실제로 오른 칸), "condition" (컨디션 변화)}
var week_schedule: Array = []

var my_school: School
var schools := {}  # id -> School (권역 + 전국)
var regional_ids: Array[String] = []
var national_ids: Array[String] = []

## 시즌 진행: 지금 단계 번호, 단계 안 몇 번째 경기, 이번 단계 순위표·대회 기록
var phase_index := 0
var phase_game := 0
var standings := {}  # 학교 id -> [승, 패, 무] (주말리그). 내 학교는 "me"
var faced_in_tournament: Array[String] = []
## 단계별 결과 요약 (시즌 결산용): [{"name", "text"}]
var phase_log: Array = []

## 인물·관계, 라이벌, 스카우트 평가, 이벤트
var people: People
var rival: Rival
var scouting: Scouting
var book: EventBook
## 폰 (문자 · 연락처에서 본 관계 문구)
var phone := Phone.new()
## 폰 뉴스 (기사 · 커뮤니티 글)
var feed := NewsFeed.new()
## 지난 경기 요약 (경기 뒤 이벤트 조건): {"result": "win"|"loss"|"draw", "hits", "home_runs",
##   "phase_end": ""|"champion"|"eliminated"|"leagueEnd"|"advanced", "national", "opponent_id", "season_over"}
var last := {}
## 받은 소식 (지난 경기 뒤에 만든다): 라이벌 기록, 유망주 랭킹 변화, 구단 관심
var news: Array[String] = []
## 지난 시즌들 요약 [{"grade", "line": SeasonStats, "phases": [...], "record": "11승 6패", "rank"}]
var history: Array = []
## 기록실 (경기별·대회별·분할 기록)
var records: CareerRecords
## 고교 졸업 진로 ("" 아직, "draft", "college")
var path := ""
## 이번 주 교류 (인물 탭 선물·만나기, 한 주에 한 번): "" 아직, 아니면 결과 문구
var week_social := ""
## 이번 주 쓴 관리 서비스 (상점 services id -> true)
var week_services := {}
## 지난 경기 뒤 들어온 돈 [[이유, 원]] (결과 화면·상점 지갑 표시)
var last_income: Array = []
## 이번 주 경기를 마쳤나 (경기 뒤 이야기·다음 주만 남았다). 불러온 뒤 같은 경기를 다시 하지 않게 한다
var game_done := false
## 이번 겨울 훈련 결과 {능력치: 오른 칸} (비어 있으면 아직). 시즌 결산에서 두 번 하지 않게 한다
var winter_gains := {}
## 이번 주 고른 훈련 id (레슨이 같은 능력치를 키운다)
var _week_training_id := ""
## 겨울 캠프 몇 주차인가 (0 = 캠프 아님, weeks 를 넘으면 캠프가 끝난 것)
var camp_week := 0
## 이번 캠프 집중 과제: 능력치 또는 CAMP_FOCUS_BODY ("" 아직 안 정함)
var camp_focus := ""
## 다음 경기 결과 고정 (이벤트의 matchOverride 효과, {} 없음): {"opponent"?, "score": [우리, 상대], "innings"?}.
## 그 경기에 내 선수는 나오지 않고, 경기가 끝나면 지운다
var match_override := {}
## 대화가 끝나면 열 미니게임 (이벤트 효과 minigame — 지금은 FitnessTest.ID 뿐, "" 이면 없음). 홈이 띄우고 끝나면 finish_minigame
var pending_minigame := ""
## 다음 경기 대본 (이벤트 효과 gamePlan, {} 없음) — GameRunner.set_plan. 경기가 끝나면 지운다
var game_plan := {}
## 체력 테스트 기록 {종목: {"grade", "value"}} (안 했으면 비어 있다)
var fitness := {}
var _checking_exhibition := false
## 캠프 시작 때 능력치 (캠프 결산에서 성장 비교)
var camp_start := {}
## 이번 캠프 연습경기 [{"opponent", "my", "opp", "result", "line"}]
var camp_games: Array = []


func _init(p_cfg: CareerConfig, player_name: String, p_batting: BattingConfig) -> void:
	cfg = p_cfg
	batting = p_batting
	player = PlayerData.create(player_name, cfg)
	var rng := _rng("create")
	player.roll_potentials(cfg, rng)
	my_school = School.create("me", cfg.my_school["name"], cfg.my_school["short"], float(cfg.teams["myTeamStrength"]), "balanced", cfg, rng)
	for d: Dictionary in cfg.regional_schools:
		var s := School.from_content(d, cfg, rng)
		schools[s.id] = s
		regional_ids.append(s.id)
	for d: Dictionary in cfg.national_schools:
		var s := School.from_content(d, cfg, rng)
		schools[s.id] = s
		national_ids.append(s.id)
	# 연습경기 상대로만 나오는 팀 (대학팀 — 리그·대회 대진에는 없다)
	for d: Dictionary in cfg.exhibition_schools:
		var s := School.from_content(d, cfg, rng)
		schools[s.id] = s
	rival = Rival.create(cfg, schools[cfg.people["rival"]["school"]], _rng("create-rival"))
	scouting = Scouting.create(cfg, _rng("create-scouting"))
	people = People.create(cfg, _rng("create-people"), {"rival": rival.player.name, "scoutTeam": scouting.scout_team_name(cfg), "player": player_name})
	# 타순에 들어가는 고정 인물 (선배 = 3번 중견수): 그 자리 선수의 이름을 인물 이름으로, 포지션은 원래 그 포지션이던 선수와 맞바꾼다
	for id: String in cfg.people:
		var l: Dictionary = cfg.people[id].get("lineup", {})
		if l.is_empty():
			continue
		var b := my_school.team.lineup[int(l["slot"]) - 1]
		for other in my_school.team.lineup:
			if other.position == l["position"]:
				other.position = b.position
		b.position = l["position"]
		b.name = people.person(id).name
	player.reputation = int(cfg.story["reputationStart"])
	player.money = int(cfg.economy["startMoney"])
	book = EventBook.new(cfg)
	records = CareerRecords.new(cfg)
	phone.add_contact("mom")
	scouting.record(prospect_rank(), player.overall())
	_start_phase()


# ---------- 시즌 단계 ----------

## 이번 시즌 단계 순서: 학년별 순서(season.phasesByGrade)가 있으면 그것, 없으면 season.phases
func phases() -> Array:
	return cfg.season.get("phasesByGrade", {}).get(str(player.grade), cfg.season["phases"])


func phase() -> Dictionary:
	return phases()[phase_index]


func is_season_over() -> bool:
	return phase_index >= phases().size()


func is_league() -> bool:
	return not is_season_over() and phase()["kind"] == "league"


func is_national() -> bool:
	return not is_season_over() and not is_league()


func phase_name() -> String:
	if is_season_over():
		return "겨울 캠프"
	var ph := phase()
	if ph["kind"] == "league":
		return ph["name"]
	return cfg.tournaments[int(ph["index"])]


## 지금 경기의 라운드 ("3/6" 또는 "8강")
func round_text() -> String:
	if is_exhibition():
		return "연습경기"
	if is_season_over():
		if not in_camp():
			return ""
		return "%d/%d주 · %s" % [camp_week, int(cfg.camp["weeks"]), "연습경기" if camp_has_game() else "훈련"]
	if is_league():
		return "%d/%d" % [phase_game + 1, regional_ids.size()]
	var rounds: Array = cfg.season["tournamentRounds"]
	return rounds[mini(phase_game, rounds.size() - 1)]


## 이번 주 상대
func opponent() -> School:
	# 경기 대본이 정한 상대 (연습경기·전국대회만 — 주말리그는 일정대로)
	if not game_plan.is_empty() and schools.has(game_plan.get("opponent", "")) and (game_plan.get("practice", false) or not is_league()):
		return schools[game_plan["opponent"]]
	if is_exhibition():
		return schools[_camp_opponent_id()]
	if is_league():
		return schools[regional_ids[phase_game]]
	# 결과를 고정한 경기는 상대도 정할 수 있다 (주말리그는 일정이 정해져 있어서 바꾸지 않는다)
	if schools.has(match_override.get("opponent", "")):
		return schools[match_override["opponent"]]
	if is_season_over():
		return schools[_camp_opponent_id()]
	return schools[_tournament_opponent_id()]


## 이번 주 경기가 대본이 만든 연습경기인가 (이벤트의 stage.practice): 순위·시즌 기록에 안 들어가고, 리그 일정은 한 주 뒤로 밀린다
func is_exhibition() -> bool:
	if not game_plan.is_empty() and not game_done:
		return game_plan.get("practice", false)
	if _checking_exhibition or game_done or is_season_over():
		return false
	_checking_exhibition = true  # 조건 검사가 다시 상대(opponent)를 물어도 돌지 않게
	var scene := book.staged_story(self, next_role())
	_checking_exhibition = false
	return not scene.is_empty() and scene["stage"].get("practice", false)


## 타순에 들어가 있는 고정 인물 (content.json people 의 lineup) 중 지금 빠져 있는 사람의 타순 번호(0~8)들:
## outFlag 플래그가 켜져 있고 backFlag 가 아직 없으면 빠진 것이다
func _lineup_out() -> Array[int]:
	var out: Array[int] = []
	for id: String in cfg.people:
		var l: Dictionary = cfg.people[id].get("lineup", {})
		if not l.is_empty() and player.grade <= int(l.get("untilGrade", 3)) and player.flags.has(l.get("outFlag", "")) and not player.flags.has(l.get("backFlag", "")):
			out.append(int(l["slot"]) - 1)
	return out


## 빠진 선수 대신 나오는 후보 (그 자리는 늘 같은 선수 — 커리어 시드로 정한다)
func _substitute(idx: int) -> Team.Batter:
	var rng := RandomNumberGenerator.new()
	rng.seed = hash([cfg.seed, "substitute", idx])
	return Team.new_batter(my_school.team.lineup[idx].position, my_school.strength - float(cfg.roster["substituteGap"]), cfg, rng)


## 홈 경기 여부 (주마다 번갈아)
func is_home() -> bool:
	return (camp_week if in_camp() else week) % 2 == 0


# ---------- 겨울 캠프 ----------

func in_camp() -> bool:
	return camp_week >= 1 and camp_week <= int(cfg.camp["weeks"])


## 캠프를 다 마쳤나 (캠프 결산 → 다음 시즌만 남았다)
func camp_finished() -> bool:
	return camp_week > int(cfg.camp["weeks"])


## 이번 캠프 주에 연습경기가 있나
func camp_has_game() -> bool:
	if not in_camp():
		return false
	for w in cfg.camp["gameWeeks"]:
		if int(w) == camp_week:
			return true
	return false


## 시즌이 끝난 뒤 캠프 시작 (1·2학년 겨울)
func start_camp() -> void:
	assert(is_season_over() and camp_week == 0, "캠프는 시즌이 끝난 뒤 한 번")
	camp_week = 1
	camp_focus = ""
	# 벤치로 시즌을 마쳤어도 캠프 연습경기에는 나간다
	if player.lineup_slot == 0:
		_lineup_by_trust()
	camp_games.clear()
	camp_start.clear()
	for s in PlayerData.STATS:
		camp_start[s] = player.stat(s)
	game_done = false
	last = {}
	news.clear()
	book.reset_week()
	week_training = ""
	week_schedule = []
	_week_training_id = ""
	week_social = ""
	week_services.clear()
	phone.deliver(self)


## 연습경기 상대: 권역·전국 학교 중 하나 (그 주 안에서는 같은 학교)
func _camp_opponent_id() -> String:
	var ids: Array[String] = regional_ids + national_ids
	return ids[_rng("camp-opponent").randi_range(0, ids.size() - 1)]


## 이 훈련의 캠프 보너스 배율 (캠프가 아니거나 휴식이면 1)
func training_multiplier(id: String) -> float:
	var stat: String = cfg.training[id]["stat"]
	if not in_camp() or stat == "":
		return 1.0
	return float(cfg.camp["focusMultiplier"] if stat == camp_focus else cfg.camp["trainingMultiplier"])


## 집중 과제 이름 ("컨택", "몸 관리")
func camp_focus_label() -> String:
	return cfg.camp["body"]["label"] if camp_focus == CAMP_FOCUS_BODY else PlayerData.STAT_LABELS[camp_focus]


## 캠프 중 컨디션 하락(훈련·연습경기)에 곱하는 배율: 집중 과제가 몸 관리면 × conditionMultiplier,
## reliefFlags 플래그가 하나라도 있으면 × flagMultiplier (캠프가 아니면 1)
func camp_condition_multiplier() -> float:
	if not in_camp():
		return 1.0
	var body: Dictionary = cfg.camp["body"]
	var m := 1.0
	if camp_focus == CAMP_FOCUS_BODY:
		m *= float(body["conditionMultiplier"])
	for f: String in body["reliefFlags"]:
		if player.flags.has(f):
			m *= float(body["flagMultiplier"])
			break
	return m


## 컨디션 변화량에 캠프 완화를 적용한다 (오르는 변화는 그대로)
func _eased(delta: int) -> int:
	return delta if delta >= 0 else roundi(delta * camp_condition_multiplier())


## 연습경기가 없는 캠프 주 마치기: 용돈·부상·연애만 흐른다. 돌려주는 값: 변화 설명 목록
func finish_camp_week() -> Array[String]:
	assert(in_camp() and not camp_has_game() and not game_done, "훈련만 하는 캠프 주가 아니다")
	game_done = true
	var notes: Array[String] = []
	_roll_injury(notes)
	_romance_week(notes)
	_pay(null, "", notes)
	last = {}
	news.clear()
	scouting.record(prospect_rank(), player.overall())
	return notes


## 연습경기 뒤: 성장·폼·컨디션·돈은 그대로, 스카우트 관심은 조금만. 순위·시즌 기록·기록실에는 넣지 않는다
func _finish_practice(game: GameRunner) -> Array[String]:
	var line := game.my_line
	var notes: Array[String] = ["연습경기 (시즌 기록에는 안 들어간다)"]
	var result := game.my_result()
	if game.me_playing:
		var raised := Growth.from_line(cfg, player, line)
		for stat: String in raised:
			notes.append("%s +%d" % [PlayerData.STAT_LABELS[stat], raised[stat]])
		_update_form(line)
		player.add_condition(_eased(int(cfg.growth["conditionPerGame"])))
		var s := cfg.scout
		var delta := roundi((line.hits * int(s["perHit"]) + line.home_runs * int(s["perHomeRun"])
			+ line.strikeouts * int(s["perStrikeout"])) * float(cfg.camp["practiceScoutMultiplier"]))
		if delta != 0:
			player.add_scout(delta)
			notes.append("스카우트 관심 %+d" % delta)
	else:
		notes.append(game.absent_text)
	_roll_injury(notes)
	_romance_week(notes)
	_pay(game, "", notes)
	if in_camp():
		camp_games.append({"opponent": game.opponent.name, "my": game.state.score[game.my_side], "opp": game.state.score[1 - game.my_side],
			"result": result, "line": line.line_text() if game.me_playing else "결장"})
	last = {"result": ["loss", "draw", "win"][result + 1], "hits": line.hits, "home_runs": line.home_runs,
		"phase_end": "", "national": false, "opponent_id": game.opponent.id, "season_over": false}
	news.clear()
	scouting.record(prospect_rank(), player.overall())
	return notes


func wins() -> int:
	return results.count(1)


func losses() -> int:
	return results.count(-1)


func _start_phase() -> void:
	phase_game = 0
	standings.clear()
	faced_in_tournament.clear()
	if not is_season_over() and is_league():
		standings["me"] = [0, 0, 0]
		for id in regional_ids:
			standings[id] = [0, 0, 0]


## 전국대회 상대: 아직 안 만난 학교 중에서. 라운드가 오를수록 강한 학교가 남는다고 보고 전력 순 뒤쪽에서 뽑는다
func _tournament_opponent_id() -> String:
	var pool: Array[String] = []
	for id in national_ids + regional_ids:
		if not faced_in_tournament.has(id):
			pool.append(id)
	pool.sort_custom(func(a: String, b: String) -> bool: return schools[a].strength < schools[b].strength)
	var rng := _rng("bracket")
	var lo := mini(int(pool.size() * phase_game * 0.25), pool.size() - 1)
	return pool[rng.randi_range(lo, pool.size() - 1)]


## 순위표 (주말리그): [[학교 이름, 승, 패, 무, 내 학교인가], ...] 승률 순
func standings_rows() -> Array:
	var rows := []
	for id: String in standings:
		var r: Array = standings[id]
		var name: String = my_school.name if id == "me" else schools[id].name
		rows.append([name, r[0], r[1], r[2], id == "me"])
	rows.sort_custom(func(a: Array, b: Array) -> bool:
		var pa: float = (a[1] + 0.5 * a[3]) / maxf(1.0, a[1] + a[2] + a[3])
		var pb: float = (b[1] + 0.5 * b[3]) / maxf(1.0, b[1] + b[2] + b[3])
		return pa > pb if pa != pb else a[1] > b[1])
	return rows


## 이번 시즌 팀 기록: 단계마다 {"name", "state": done|now|next, "text"}.
## 끝난 단계는 phase_log 결과("3위 (4승 2패 0무)" · "8강 탈락" · "우승!"), 지금 단계는 주말리그면 지금 순위·전적, 전국대회면 오른 라운드
func season_board() -> Array:
	var out := []
	for i in phases().size():
		var ph: Dictionary = phases()[i]
		var name: String = ph["name"] if ph["kind"] == "league" else cfg.tournaments[int(ph["index"])]
		if i < phase_log.size():
			out.append({"name": name, "state": "done", "text": phase_log[i]["text"]})
		elif i == phase_index:
			var text := ""
			if is_league():
				var r: Array = standings["me"]
				if r[0] + r[1] + r[2] == 0:
					text = "개막 앞둠"
				else:
					text = "%d위 · %d승 %d패%s" % [my_rank(), r[0], r[1], " %d무" % r[2] if r[2] > 0 else ""]
			else:
				var rounds: Array = cfg.season["tournamentRounds"]
				text = "%s 진출" % rounds[mini(phase_game, rounds.size() - 1)] if phase_game > 0 else "%s 앞둠" % rounds[0]
			out.append({"name": name, "state": "now", "text": text})
		else:
			out.append({"name": name, "state": "next", "text": "예정"})
	return out


## 주말리그 순위표에서 우리 학교 순위 (1부터)
func my_rank() -> int:
	var rows := standings_rows()
	for i in rows.size():
		if rows[i][4]:
			return i + 1
	return rows.size()


## 승·패·무 → 순위표 칸 번호
static func _slot(result: int) -> int:
	return 0 if result > 0 else (1 if result < 0 else 2)


# ---------- 주간 ----------

## 평일 칸 이름 ("월·화" …)
func schedule_slots() -> Array:
	return cfg.schedule["slots"]


## 스케줄 칸에 훈련을 넣는다 (실행 전만)
func set_plan(slot: int, id: String) -> void:
	assert(cfg.training.has(id), "모르는 훈련: %s" % id)
	_fill_plan_size()
	if week_training == "":
		week_plan[slot] = id


## 모든 칸이 찼나
func plan_ready() -> bool:
	_fill_plan_size()
	return not week_plan.has("")


func _fill_plan_size() -> void:
	var n := schedule_slots().size()
	while week_plan.size() < n:
		week_plan.append("")
	week_plan.resize(n)


## 이 훈련 칸 하나의 컨디션 변화 (한 주 몫 ÷ 칸 수, 캠프 완화 반영)
func slot_condition(id: String) -> int:
	return _eased(roundi(float(cfg.training[id]["condition"]) / schedule_slots().size()))


## 이 훈련 칸이 대성공·실패할 확률 (지금 컨디션 기준): {"great", "fail"}. 휴식은 둘 다 0
func slot_chances(id: String, condition: int = -1) -> Dictionary:
	if cfg.training[id]["stat"] == "":
		return {"great": 0.0, "fail": 0.0}
	var c := cfg.schedule
	var cond := player.condition if condition < 0 else condition
	var great: float = float(c["greatChance"]) + maxf(0.0, cond - float(c["greatPivot"])) * float(c["greatPerPoint"])
	var fail: float = float(c["failChance"]) + maxf(0.0, float(c["failPivot"]) - cond) * float(c["failPerPoint"])
	fail = clampf(fail, 0.0, 1.0)
	return {"great": clampf(great, 0.0, 1.0 - fail), "fail": fail}


## 스케줄 실행: 칸을 차례로 (앞 칸의 컨디션 변화가 뒤 칸 확률에 들어간다). 부상 중이면 모든 칸이 휴식.
## 돌려주는 값: 칸별 결과 (week_schedule). 이미 했으면 그 결과를 그대로
func run_schedule() -> Array:
	if week_training != "":
		return week_schedule
	assert(plan_ready(), "스케줄 칸이 비었다")
	var slots := schedule_slots()
	var share := 1.0 / slots.size()
	var rng := _rng("schedule")
	week_schedule = []
	var labels := []
	var sums := {}  # 능력치 -> 오른 성장량 합
	var cond_total := 0
	for i in slots.size():
		var id: String = "rest" if player.is_injured() else week_plan[i]
		var t: Dictionary = cfg.training[id]
		var stat: String = t["stat"]
		var r := {"slot": slots[i], "id": id, "label": t["label"], "outcome": "rest", "outcome_label": "",
			"stat": stat, "amount": 0.0, "raised": 0, "condition": 0}
		if stat != "":
			var ch := slot_chances(id)
			var roll := rng.randf()
			var outcome := "fail" if roll < ch["fail"] else ("great" if roll < ch["fail"] + ch["great"] else "success")
			var o: Dictionary = cfg.schedule["outcomes"][outcome]
			var base := float(t["gain"]) * share * training_multiplier(id) * float(o["gainMul"])
			r["outcome"] = outcome
			r["outcome_label"] = o["label"]
			r["amount"] = Growth.amount(cfg, player.stat(stat), player.potential[stat], base, player.grade)
			r["raised"] = Growth.apply(cfg, player, stat, base)
			sums[stat] = float(sums.get(stat, 0.0)) + float(r["amount"])
			_week_training_id = id
		var cond := slot_condition(id)
		player.add_condition(cond)
		r["condition"] = cond
		cond_total += cond
		week_schedule.append(r)
		labels.append(t["label"])
	last_training = " · ".join(labels)
	var parts := []
	for st: String in sums:
		parts.append("%s +%.1f" % [PlayerData.STAT_LABELS[st], sums[st]])
	var greats := week_schedule.filter(func(x: Dictionary) -> bool: return x["outcome"] == "great").size()
	var fails := week_schedule.filter(func(x: Dictionary) -> bool: return x["outcome"] == "fail").size()
	# 캠프 보너스가 붙은 칸이 있으면 알려 준다
	for id: String in week_plan:
		var mult := training_multiplier(id)
		if mult != 1.0 and not player.is_injured():
			parts.append("%s ×%.1f" % ["집중 과제" if cfg.training[id]["stat"] == camp_focus else "캠프 보너스", mult])
			break
	if greats > 0:
		parts.append("대성공 %d" % greats)
	if fails > 0:
		parts.append("실패 %d" % fails)
	parts.append("컨디션 %+d" % cond_total)
	week_training = "%s: %s" % [last_training, ", ".join(parts)]
	return week_schedule


## 한 주를 한 가지 훈련으로 채우고 바로 실행 (자동 진행·테스트용 지름길). 돌려주는 값: 결과 요약 한 줄
func train(id: String) -> String:
	if week_training != "":
		return week_training
	_fill_plan_size()
	for i in week_plan.size():
		week_plan[i] = id
	run_schedule()
	return week_training


## 다음 경기에 내가 어떻게 나가나: "start" 선발 | "pinch" 벤치에서 시작해 대타 | "bench" 벤치 (출전 없음) | "out" 결장 (부상, 결과를 고정한 경기).
## 타순 0(벤치)이면 이야기가 부르는 경기(조건이 맞는 한 번뿐인 pinch_pa 이벤트)에만 대타로 나간다
func next_role() -> String:
	if not game_plan.is_empty() and not player.is_injured():
		return "start" if game_plan.get("role", "start") == "start" else "pinch"
	if player.is_injured() or not match_override.is_empty():
		return "out"
	if player.bench_next:
		return "pinch"
	if player.lineup_slot == 0:
		return "pinch" if book.has_game_story(self, "pinch_pa") else "bench"
	return "start"


## 다음 경기 우리 팀 선발 타순: [{"slot", "name", "me"}]. 내가 결장·벤치 대기면 내 자리는 원래 명단 선수다 (new_game 과 같은 기준)
func next_lineup() -> Array:
	var starting := next_role() == "start"
	var rows := []
	var out := _lineup_out()
	for i in my_school.team.lineup.size():
		var me := starting and i == player.lineup_slot - 1
		var other: String = _substitute(i).name if out.has(i) else my_school.team.lineup[i].name
		rows.append({"slot": i + 1, "name": player.name if me else other, "me": me})
	return rows


func new_game() -> GameRunner:
	player.game_boost.clear()
	if not game_plan.is_empty() and not player.is_injured():
		return _new_plan_game()
	var role := next_role()
	var scene := book.staged_story(self, role)
	var home: bool = is_home() if scene.is_empty() else scene["stage"]["half"] == "bottom"
	if match_override.has("home"):
		home = bool(match_override["home"])
	var game := GameRunner.new(cfg, batting, player, my_school, opponent(), home, _seed("game"), role in ["start", "pinch"], role == "pinch")
	game.pinch_late = role == "pinch" and not player.bench_next
	if not scene.is_empty():
		game.set_stage(scene["stage"], scene["moment"])
		game.exhibition = scene["stage"].get("practice", false)
	for idx: int in _lineup_out():
		game.replace_starter(idx, _substitute(idx))
	if role == "bench":
		game.absent_text = "벤치 (출전 없음)"
	elif not match_override.is_empty():
		game.absent_text = "출전하지 않았다"
		var score: Array = match_override["score"]
		game.fix_result(int(score[0]), int(score[1]), int(match_override.get("innings", cfg.innings)), match_override.get("line", []))
		game.rival_line = match_override.get("rivalLine", {})
	if player.is_injured() and player.injury_label != "":
		game.absent_text = "%s — 결장" % player.injury_label
	return game


## 경기 대본(gamePlan)이 있는 경기: 장면마다 정해진 상황에서 내 타석·수비를 직접 한다.
## 홈·원정은 대본의 home, 없으면 첫 장면이 정한다 (내 타석 장면이 말이면 홈, 수비 장면이 초면 홈)
func _new_plan_game() -> GameRunner:
	var plan := game_plan
	var first: Dictionary = plan["scenes"][0]
	var home: bool = (first["half"] == "bottom") == (first["kind"] == "pa")
	if plan.has("home"):
		home = bool(plan["home"])
	var game := GameRunner.new(cfg, batting, player, my_school, opponent(), home, _seed("game"), true, plan.get("role", "start") == "sub")
	for idx: int in _lineup_out():
		game.replace_starter(idx, _substitute(idx))
	game.cond_test = func(c: Dictionary) -> bool: return EventBook.test(self, c)
	game.set_plan(plan)
	game.exhibition = plan.get("practice", false)
	return game


## 경기 대본 장면 직전에 띄울 대화 (없으면 빈 Dictionary): 지금 장면의 event, 아직 안 봤을 때만. 한 주 이벤트 개수에 넣지 않는다
func scene_event(game: GameRunner) -> Dictionary:
	var sc := game.current_scene()
	var id: String = sc.get("event", "")
	if id == "" or not book.defs.has(id) or book.fired.has(id):
		return {}
	return book.defs[id]


## 경기 중 대화: 지금 내 타석이 어떤 순간인가 ("pinch_pa" 대타로 나가는 타석 | "first_pa" 첫 타석 | "clutch_pa" 승부처 | "" 평범한 타석).
## 한 번뿐인 승부처 스토리가 기다리고 있으면 clutch.minInning 회 이후의 내 타석은 점수 차·주자와 상관없이 승부처로 본다
## (진짜 승부처는 두 경기에 한 번도 안 와서, 기다리기만 하면 그 뒤 이야기가 시즌 안에 안 열린다)
func pa_moment(game: GameRunner) -> String:
	if game.planned:
		return "scene"
	if game.staged and game.my_line.pa == 0:
		return game.stage_moment
	if game.my_line.pa == 0:
		return "pinch_pa" if game.pinch_hit else "first_pa"
	if game.is_clutch():
		return "clutch_pa"
	if game.state.inning >= int(cfg.story["clutch"]["minInning"]) and book.has_game_story(self, "clutch_pa"):
		return "clutch_pa"
	return ""


## 경기 중 대화: 내 타석 결과가 만든 순간 ("after_homer" | "after_strikeout" | "")
static func result_moment(outcome: SwingJudge.Outcome) -> String:
	match outcome:
		SwingJudge.Outcome.HOME_RUN:
			return "after_homer"
		SwingJudge.Outcome.STRIKEOUT:
			return "after_strikeout"
	return ""


## 이 순간에 띄울 경기 중 이벤트 (없으면 빈 Dictionary). 한 주 이벤트 개수에 같이 들어간다
func game_event(moment: String, game: GameRunner) -> Dictionary:
	if moment == "scene":
		return scene_event(game)
	if moment == "" or game.planned:
		return {}
	return book.pick_game(self, moment, "pa%d" % game.my_line.pa)


## 화면 없이 경기를 돌릴 때(테스트·빨리 감기·튜닝 도구) 내 타석 직전에 부른다: 이 순간의 한 번뿐인 경기 중 스토리를 첫 선택지로 넘긴다.
## 안 넘기면 그 이야기가 계속 기다려서 뒤에 이어지는 이야기가 열리지 않는다 (대타 호출이면 매 경기 대타로 불린다).
## 되풀이되는 이야기·무작위 이벤트는 건드리지 않는다
func auto_game_story(game: GameRunner) -> void:
	if game.planned:
		var se := scene_event(game)
		if not se.is_empty():
			resolve_event(se, 0)
		return
	var moment := pa_moment(game)
	if moment != "" and book.has_game_story(self, moment):
		var e := game_event(moment, game)
		if not e.is_empty():
			resolve_event(e, 0)


## 내 타석 시드 (몇 번째 타석인지로 구분)
func at_bat_seed(game: GameRunner) -> int:
	return _seed("atbat%d" % game.my_line.pa)


## 내 도루 기회의 시드 (몇 회 · 내 몇 번째 타석 뒤 · 어느 타자 때인지로 구분)
func steal_seed(game: GameRunner) -> int:
	return _seed("steal%d:%d:%d" % [game.state.inning, game.my_line.pa, game.state.batter_index[game.my_side]])


## 경기가 끝난 뒤: 기록·성장·폼·부상·스카우트 관심도·라이벌·유망주 풀·순위표/대회 진행. 돌려주는 값: 변화 설명 목록
func finish_game(game: GameRunner) -> Array[String]:
	assert(not game_done, "이번 주 경기는 이미 끝났다")
	game_done = true
	player.game_boost.clear()
	player.bench_next = false
	match_override = {}
	game_plan = {}
	_count_videos(game)
	if in_camp() or game.exhibition:
		return _finish_practice(game)
	var line := game.my_line
	var g := cfg.growth
	var notes: Array[String] = []
	var result := game.my_result()
	results.append(result)
	var national := is_national()
	if game.me_playing:
		player.season.merge(line)
		player.season.games += 1
		var raised := Growth.from_line(cfg, player, line)
		for stat: String in raised:
			notes.append("%s +%d" % [PlayerData.STAT_LABELS[stat], raised[stat]])
		_update_form(line)
		player.add_condition(int(g["conditionPerGame"]))
		# 스카우트 관심도 (전국대회는 더 크게)
		var s := cfg.scout
		var mult: float = float(s["nationalMultiplier"]) if national else 1.0
		var delta := roundi((line.hits * int(s["perHit"]) + line.home_runs * int(s["perHomeRun"])
			+ line.strikeouts * int(s["perStrikeout"]) + (int(s["perWin"]) if result > 0 else 0)) * mult)
		if delta != 0:
			player.add_scout(delta)
			notes.append("스카우트 관심 %+d" % delta)
	else:
		notes.append(game.absent_text)
	_roll_injury(notes)
	_romance_week(notes)
	var teams_before := scouting.interested_teams(cfg, player.scout_interest, player.grade).size()
	rival.play_week(cfg, batting, game, _rng("rival"))
	scouting.weekly()
	records.add_game(_game_entry(game, result), game.my_pas)
	var phase_end := _advance_phase(game, result, notes)
	if phase_end in ["leagueEnd", "champion", "eliminated"]:
		records.close_phase(phase_log[-1]["text"])
	_pay(game, phase_end, notes)
	last = {"result": ["loss", "draw", "win"][result + 1], "hits": line.hits, "home_runs": line.home_runs,
		"phase_end": phase_end, "national": national, "opponent_id": game.opponent.id, "season_over": is_season_over()}
	scouting.record(prospect_rank(), player.overall())
	_build_news(teams_before)
	feed.after_game(self, game, records.games[-1], phase_end, teams_before)
	return notes


## 폰 탭 배지: 안 읽은 문자 + 안 읽은 기사
func phone_unread() -> int:
	return phone.total_unread() + feed.unread_count()


## 소율의 영상 수: 내가 직접 한 실전 타석·수비마다 +1 (연습경기도 실전이다)
func _count_videos(game: GameRunner) -> void:
	if not game.me_playing:
		return
	if cfg.vars.has(VIDEO_AT_BAT):
		player.vars[VIDEO_AT_BAT] = int(EventBook.var_of(self, VIDEO_AT_BAT)) + game.my_line.pa
	if cfg.vars.has(VIDEO_FIELDING):
		player.vars[VIDEO_FIELDING] = int(EventBook.var_of(self, VIDEO_FIELDING)) + game.my_fields.size()


## 기록실에 넣을 경기 한 줄 (단계가 넘어가기 전에 만든다 — 대회 이름·라운드가 이 경기 것이어야 한다)
func _game_entry(game: GameRunner, result: int) -> CareerRecords.GameEntry:
	var e := CareerRecords.GameEntry.new()
	e.season_no = season_no
	e.grade = player.grade
	e.week = week
	e.phase_name = phase_name()
	e.round = round_text()
	e.national = is_national()
	e.opponent = game.opponent.name
	e.home = game.my_side == GameState.HOME
	e.my_score = game.state.score[game.my_side]
	e.opp_score = game.state.score[1 - game.my_side]
	e.result = result
	e.playing = game.me_playing
	if game.me_playing:
		e.line.merge(game.my_line)
		e.line.games = 1
	return e


## 고교 통산 기록 (지난 시즌들 + 이번 시즌)
func career_line() -> PlayerData.SeasonStats:
	var total := PlayerData.SeasonStats.new()
	for h: Dictionary in history:
		total.merge(h["line"])
	total.merge(player.season)
	return total


## 경기 뒤 수입: 용돈(엄마 관계) + 칭찬 용돈(안타·홈런) + 상금(주말리그 1위·전국대회 우승)
func _pay(game: GameRunner, phase_end: String, notes: Array[String]) -> void:
	var e := cfg.economy
	last_income.clear()
	var allowance := maxi(int(e["allowanceMin"]), int(e["allowanceBase"]) + (people.relation("mom") - 50) * int(e["allowancePerMomPoint"]))
	last_income.append(["용돈", allowance])
	if game != null and game.me_playing:
		var praise := game.my_line.hits * int(e["perHit"]) + game.my_line.home_runs * int(e["perHomeRun"])
		if praise > 0:
			last_income.append(["칭찬 용돈", praise])
	if phase_end == "champion":
		last_income.append(["동문회 장학금 (우승)", int(e["championPrize"])])
	elif phase_end == "leagueEnd" and phase_log[-1]["text"].begins_with("1위"):
		last_income.append(["동문회 장학금 (리그 1위)", int(e["leagueWinPrize"])])
	var total := 0
	for pair: Array in last_income:
		total += int(pair[1])
	player.money += total
	notes.append("돈 +%s" % PlayerData.money_text(total))


## 연애 중이면: 호감이 조금씩 식고(만나야 채워진다), 컨디션은 조금 오른다
func _romance_week(notes: Array[String]) -> void:
	if people.partner == "":
		return
	people.add_relation("partner", -int(cfg.romance["weeklyDecay"]))
	player.add_condition(int(cfg.romance["datingCondition"]))


# ---------- 상점·교류 ----------

## 장비 사기. 돌려주는 값: "" 성공, 아니면 못 사는 이유
func buy_gear(id: String) -> String:
	var item: Dictionary = cfg.shop["gear"][id]
	if player.gear.has(id):
		return "이미 있다"
	if player.money < int(item["price"]):
		return "돈이 모자라다"
	player.money -= int(item["price"])
	player.gear[id] = true
	return ""


## 관리 서비스 (한 주에 한 번씩). 돌려주는 값: 결과 문구 (못 쓰면 "!" 로 시작하는 이유)
func use_service(id: String) -> String:
	var sv: Dictionary = cfg.shop["services"][id]
	var why := service_block(id)
	if why != "":
		return "!" + why
	player.money -= int(sv["price"])
	week_services[id] = true
	var parts := []
	match id:
		"lesson":
			var stat: String = cfg.training[_week_training_id]["stat"]
			var raised := Growth.apply(cfg, player, stat, float(sv["gain"]))
			parts.append("%s %s" % [PlayerData.STAT_LABELS[stat], "+%d" % raised if raised > 0 else "조금 성장"])
		"care":
			player.add_condition(int(sv["condition"]))
			parts.append("컨디션 %+d" % int(sv["condition"]))
			if player.is_injured():
				player.injury_weeks = maxi(0, player.injury_weeks - int(sv["injuryWeeks"]))
				parts.append("부상 회복 %d주 당김" % int(sv["injuryWeeks"]))
		"meal":
			player.add_condition(int(sv["condition"]))
			player.form = minf(player.form + float(sv["form"]), float(cfg.form["max"]))
			parts.append("컨디션 %+d, 폼 ▲" % int(sv["condition"]))
	return "%s: %s" % [sv["label"], ", ".join(parts)]


## 서비스를 못 쓰는 이유 ("" 쓸 수 있음)
func service_block(id: String) -> String:
	var sv: Dictionary = cfg.shop["services"][id]
	if week_services.has(id):
		return "이번 주엔 이미 했다"
	if player.money < int(sv["price"]):
		return "돈이 모자라다"
	if id == "lesson" and (_week_training_id == "" or cfg.training[_week_training_id]["stat"] == ""):
		return "훈련을 한 주에만 (휴식 주 제외)"
	return ""


## 인물 교류 (한 주에 한 번): kind = "gift" (선물) | "hangout" (만나기, 연애 후보만). 돌려주는 값: 결과 문구 (못 하면 "!이유")
func socialize(person_id: String, kind: String) -> String:
	var why := social_block(person_id, kind)
	if why != "":
		return "!" + why
	var e := cfg.economy
	var person := people.person(person_id)
	var parts := []
	if kind == "gift":
		player.money -= int(e["giftCost"])
		people.add_relation(person_id, int(e["giftRel"]))
		parts.append("%s %+d" % [EventBook.rel_name(person), int(e["giftRel"])])
		week_social = "%s에게 선물: %s" % [person.call, ", ".join(parts)]
	else:
		player.money -= int(e["hangoutCost"])
		people.add_relation(person_id, int(e["hangoutRel"]))
		player.add_condition(int(e["hangoutCondition"]))
		parts.append("%s %+d" % [EventBook.rel_name(person), int(e["hangoutRel"])])
		parts.append("컨디션 %+d" % int(e["hangoutCondition"]))
		week_social = "%s과(와) %s: %s" % [person.call, "데이트" if people.partner == person_id else "만남", ", ".join(parts)]
	week_social = EventBook.josa(week_social)
	return week_social


func social_block(person_id: String, kind: String) -> String:
	var person := people.person(person_id)
	if week_social != "":
		return "교류는 한 주에 한 번"
	if person == null or not person.met:
		return "아직 모르는 사람"
	var cost := int(cfg.economy["giftCost"] if kind == "gift" else cfg.economy["hangoutCost"])
	if kind == "hangout" and not person.romance:
		return "만나기는 연애 후보만"
	if player.money < cost:
		return "돈이 모자라다"
	return ""


## 교류할 수 있는 인물인가 (감독·코치·스카우트는 선물 금지 — 뇌물이 된다, 라이벌은 안 받는다)
static func can_gift(person: People.Person) -> bool:
	return not person.id in ["coach", "batting_coach", "scout", "rival"]


## 받은 소식 (허브 INBOX)
func _build_news(teams_before: int) -> void:
	news.clear()
	if people.is_met("rival") and rival.last_line != null:
		var rs := rival.player.season
		news.append("라이벌 · %s %s%s (시즌 %s · %d홈런)" % [rival.player.name, rival.last_line.line_text(),
			" — 우리와 맞대결" if rival.last_vs_me else "", rs.average(), rs.home_runs])
	var rank_change := -Scouting.delta(scouting.rank_history, 1)
	if rank_change != 0:
		news.append("스카우트 · 유망주 랭킹 %d위 (%s%d)" % [prospect_rank(), "▲" if rank_change > 0 else "▼", absi(rank_change)])
	var teams := scouting.interested_teams(cfg, player.scout_interest, player.grade)
	if teams.size() > teams_before:
		news.append("스카우트 · %s 구단이 지켜보기 시작했다" % teams[-1]["name"])


## 폼: 지난 폼이 조금 남고 + 흔들림 + 기대보다 잘 치면 오른다
func _update_form(line: PlayerData.SeasonStats) -> void:
	var f := cfg.form
	var rng := _rng("form")
	var expected := line.ab * float(f["expectedAvg"])
	var v := player.form * float(f["decay"]) + rng.randfn(0.0, float(f["weeklySd"])) + (line.hits - expected) * float(f["perHitAboveExpected"])
	player.form = clampf(v, -float(f["max"]), float(f["max"]))


## 부상: 지쳐 있을수록 잘 다친다. 이미 다친 상태면 회복을 한 주 진행
func _roll_injury(notes: Array[String]) -> void:
	if player.is_injured():
		player.injury_weeks -= 1
		if not player.is_injured():
			player.injury_label = ""
			notes.append("부상에서 돌아왔다")
		return
	var inj := cfg.injury
	if player.grade < int(inj["minGrade"]):
		return
	var chance := float(inj["base"]) + maxf(0.0, float(inj["threshold"]) - player.condition) * float(inj["perPointBelow"])
	var rng := _rng("injury")
	if rng.randf() < chance:
		player.injury_weeks = rng.randi_range(int(inj["weeksMin"]), int(inj["weeksMax"]))
		player.injury_label = ""
		notes.append("부상! %d주 결장" % player.injury_weeks)


## 순위표·대회 진행. 돌려주는 값: 단계가 어떻게 됐나 ("" 계속, "leagueEnd", "advanced", "champion", "eliminated")
func _advance_phase(game: GameRunner, result: int, notes: Array[String]) -> String:
	var name := phase_name()
	if is_league():
		standings["me"][_slot(result)] += 1
		standings[game.opponent.id][_slot(-result)] += 1
		_sim_other_league_games(game.opponent.id)
		phase_game += 1
		if phase_game >= regional_ids.size():
			var rows := standings_rows()
			var rank := 1
			for i in rows.size():
				if rows[i][4]:
					rank = i + 1
			var r: Array = standings["me"]
			phase_log.append({"name": name, "text": "%d위 (%d승 %d패 %d무)" % [rank, r[0], r[1], r[2]]})
			notes.append("%s %d위로 마감" % [name, rank])
			_next_phase()
			return "leagueEnd"
		return ""
	faced_in_tournament.append(game.opponent.id)
	var rounds: Array = cfg.season["tournamentRounds"]
	var round_name: String = rounds[mini(phase_game, rounds.size() - 1)]
	if result > 0:
		phase_game += 1
		if phase_game >= rounds.size():
			phase_log.append({"name": name, "text": "우승!"})
			notes.append("%s 우승!" % name)
			player.add_scout(int(cfg.scout["perHomeRun"]) * 2)
			_next_phase()
			return "champion"
		notes.append("%s %s 진출" % [name, rounds[phase_game]])
		return "advanced"
	else:
		# 지면 탈락. 실제 고교 토너먼트는 승부치기로 가리지만 프로토타입은 무승부도 탈락으로 본다
		phase_log.append({"name": name, "text": "%s 탈락" % round_name})
		notes.append("%s %s 탈락" % [name, round_name])
		_next_phase()
		return "eliminated"


func _next_phase() -> void:
	phase_index += 1
	_start_phase()


## 같은 주 다른 권역 학교끼리의 경기 (전력 차로 승패만). 짝은 주차마다 돌린다
func _sim_other_league_games(opponent_id: String) -> void:
	var others: Array[String] = []
	for id in regional_ids:
		if id != opponent_id:
			others.append(id)
	var rng := _rng("league")
	var n := others.size()
	for i in range(0, n - 1, 2):
		var a: String = others[(i + week) % n]
		var b: String = others[(i + 1 + week) % n]
		if a == b:
			continue
		var r := quick_result(schools[a].strength, schools[b].strength, rng)
		standings[a][_slot(r)] += 1
		standings[b][_slot(-r)] += 1


## 전력 a 대 b 의 빠른 결과 (1 a 승, −1 b 승, 0 무)
func quick_result(a: float, b: float, rng: RandomNumberGenerator) -> int:
	if rng.randf() < float(cfg.season["drawChance"]):
		return 0
	var p := 1.0 / (1.0 + pow(10.0, -(a - b) / float(cfg.season["winProbScale"])))
	return 1 if rng.randf() < p else -1


# ---------- 이벤트 ----------

## 이번 주 이 시점(start 주 시작 | after 경기 뒤 | seasonEnd 시즌 결산)에 아직 고르지 않은 이벤트들
func events(timing: String) -> Array:
	return book.pending(self, timing)


## 이벤트 선택. 돌려주는 값: 결과 문장 + 변화 설명.
## 화면 없이 고르는 길이라, 대화가 미니게임을 열었으면 autoGrade 결과로 바로 끝낸다 (테스트·빨리 감기가 멈추지 않게)
func resolve_event(event: Dictionary, choice_index: int) -> String:
	var text := book.resolve(self, event, choice_index)
	if pending_minigame != "":
		auto_minigame()
	return text


## 화면 없이 미니게임을 끝낸다 (테스트·빨리 감기·건너뛰기): 체력 테스트는 autoGrade, 연습은 autoRate 만큼 성공
func auto_minigame() -> void:
	if pending_minigame.begins_with("drill:"):
		var d: Dictionary = cfg.drills[pending_minigame.trim_prefix("drill:")]
		finish_drill(roundi(float(d["balls"]) * drill_auto_rate(d)))
	else:
		finish_minigame(FitnessTest.auto_results(cfg.fitness, minigame_kinds()))


## 지금 미니게임이 체력 테스트의 어느 종목들인가 (fitness_test 면 세 종목 모두, fitness_run 이면 50m 하나)
func minigame_kinds() -> Array:
	if pending_minigame == FitnessTest.ID:
		return cfg.fitness["order"]
	return [pending_minigame.trim_prefix("fitness_")]


## 체력 테스트 종목이 끝났다 (한 번에 세 종목이든, 종목 하나든): 기록을 남기고 그 종목의 보상을 준다.
## 세 종목이 다 모이면 종합 등급 보상도 (한 번만). 시나리오 변수(test_*_grade)도 세운다 (balance.json fitness.story).
## 돌려주는 값: 변화 설명들 ("주력 +3", "감독 신뢰 +5" …)
func finish_minigame(results: Dictionary) -> Array[String]:
	pending_minigame = ""
	var fresh := {}
	for kind: String in results:
		if not fitness.has(kind):
			fresh[kind] = results[kind]
		fitness[kind] = results[kind].duplicate(true)
	var parts := book.apply_effects(self, FitnessTest.kind_effects(cfg.fitness, fresh))
	var story: Dictionary = cfg.fitness.get("story", {})
	for kind: String in results:
		var vid: String = story.get("vars", {}).get(kind, "")
		if cfg.vars.has(vid):
			player.vars[vid] = story["grades"][results[kind]["grade"]]
	if not all_fitness_done() or player.flags.has("fitness_done"):
		return parts
	parts.append_array(book.apply_effects(self, FitnessTest.overall_effects(cfg.fitness, fitness)))
	if cfg.vars.has(story.get("overallVar", "")):
		player.vars[story["overallVar"]] = FitnessTest.story_overall(cfg.fitness, fitness)
	var grades := []
	for kind: String in cfg.fitness["order"]:
		grades.append("%s %s" % [minigame_text("events.%s.short" % kind), fitness[kind]["grade"]])
	var row := FitnessTest.overall(cfg.fitness, fitness)
	news.append("체력 테스트 · 종합 %s (%s)" % [row["grade"], " · ".join(grades)])
	book.log.append({"season": season_no, "grade": player.grade, "week": week, "title": minigame_text("title"),
		"speaker": "", "choice": " · ".join(grades), "result": "종합 %s" % row["grade"], "story": true})
	return parts


## 체력 테스트 세 종목을 다 했나
func all_fitness_done() -> bool:
	for kind: String in cfg.fitness["order"]:
		if not fitness.has(kind):
			return false
	return true


# ---------- 이야기 속 연습 (drill) ----------

## 지금 띄울 연습 정의 (pending_minigame 이 drill:<id> 일 때, 아니면 {})
func pending_drill() -> Dictionary:
	if not pending_minigame.begins_with("drill:"):
		return {}
	var d: Dictionary = cfg.drills[pending_minigame.trim_prefix("drill:")].duplicate(true)
	d["id"] = pending_minigame.trim_prefix("drill:")
	return d


## 이 연습에 붙는 보정 {능력치: +n}: boostByVar [{"var", "stat", "values"?: {값: n}, "perPoint"?, "offset"?}]
## — 체력 테스트 등급이 좋으면 판정 폭이 넓어지고, 폼 적응도가 낮으면 좁아진다
func drill_boost(d: Dictionary) -> Dictionary:
	var out := {}
	for b: Dictionary in d.get("boostByVar", []):
		var v: Variant = EventBook.var_of(self, b["var"])
		var n := 0.0
		if b.has("values"):
			n = float(b["values"].get(str(v), 0))
		else:
			n = float(b.get("offset", 0)) + float(v) * float(b.get("perPoint", 0))
		out[b["stat"]] = int(out.get(b["stat"], 0)) + roundi(n)
	return out


## 화면 없이 할 때 성공 비율 (autoRate + 보정 1점당 autoPerBoost)
func drill_auto_rate(d: Dictionary) -> float:
	var boost := 0
	for st: String in drill_boost(d):
		boost += int(drill_boost(d)[st])
	return clampf(float(d.get("autoRate", 0.5)) + boost * float(d.get("autoPerBoost", 0.0)), 0.0, 1.0)


## 연습이 끝났다: 성공 수만큼 경험치(stat × gainPerSuccess), 결과 변수(countVar)에 성공 수. 돌려주는 값: 변화 설명들
func finish_drill(successes: int) -> Array[String]:
	var d := pending_drill()
	pending_minigame = ""
	var fx := {}
	if d.has("stat") and successes > 0:
		fx["train"] = {d["stat"]: float(d["gainPerSuccess"]) * successes}
	if d.has("countVar") and cfg.vars.has(d["countVar"]):
		fx["set"] = {d["countVar"]: successes}
	var parts := book.apply_effects(self, fx)
	book.log.append({"season": season_no, "grade": player.grade, "week": week, "title": d.get("title", d["id"]),
		"speaker": "", "choice": "", "result": "%d / %d" % [successes, int(d["balls"])], "story": true})
	return parts


## 미니게임 글 (content.json minigames.fitness_test, "events.run.title" 처럼 점으로 찾아 들어간다). 없으면 ""
func minigame_text(path: String) -> String:
	var v: Variant = cfg.minigames.get(FitnessTest.ID, {})
	for key in path.split("."):
		if not (v is Dictionary and (v as Dictionary).has(key)):
			return ""
		v = v[key]
	return EventBook.fill(self, str(v))


## 같은 학년 유망주 중 내 순위 (라이벌 포함)
func prospect_rank() -> int:
	return scouting.rank_of(Scouting.value_of(cfg, player), [Scouting.value_of(cfg, rival.player)])


func rival_rank() -> int:
	return scouting.rank_of(Scouting.value_of(cfg, rival.player), [Scouting.value_of(cfg, player)])


## 유망주 랭킹 표: [{"rank", "name", "me", "rival"}] 평가값 순
func prospect_board() -> Array:
	var rows := []
	for i in scouting.pool.size():
		rows.append({"value": scouting.pool[i], "name": scouting.names[i], "me": false, "rival": false})
	rows.append({"value": Scouting.value_of(cfg, player), "name": player.name, "me": true, "rival": false})
	rows.append({"value": Scouting.value_of(cfg, rival.player), "name": rival.player.name, "me": false, "rival": true})
	rows.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a["value"] > b["value"])
	for i in rows.size():
		rows[i]["rank"] = i + 1
	return rows


## 예상 지명 {"big", "suffix", "text"}
func projection() -> Dictionary:
	return Scouting.projection(cfg, prospect_rank(), player.grade)


## 이벤트가 없는 주의 감독 한마디 (결과와 내 활약으로 고른다)
func coach_line(game: GameRunner) -> String:
	var result_key: String = ["loss", "draw", "win"][game.my_result() + 1]
	var form_key := "hot" if game.my_line.hits >= 2 else "cold"
	var lines: Array = cfg.coach_lines[result_key + "_" + form_key]
	return lines[_rng("coach").randi_range(0, lines.size() - 1)]


func advance_week() -> void:
	game_done = false
	if camp_week > 0:
		camp_week += 1  # 캠프 중엔 시즌 주차가 멈춘다
	else:
		week += 1
	total_week += 1
	week_training = ""
	week_schedule = []
	_week_training_id = ""
	week_social = ""
	week_services.clear()
	phone.deliver(self)


# ---------- 시즌 끝 ----------

## 겨울 훈련 성장 (시즌이 끝날 때 한 번 — 다시 부르면 처음 결과를 그대로 돌려준다). 돌려주는 값: {능력치: 오른 칸}
func winter_training() -> Dictionary:
	if not winter_gains.is_empty():
		return winter_gains
	for s in PlayerData.STATS:
		winter_gains[s] = Growth.apply(cfg, player, s, float(cfg.growth["winterGain"]))
	return winter_gains


## 타순을 감독 신뢰로 정한다 (career.story.lineupByTrust)
func _lineup_by_trust() -> void:
	for pair: Array in cfg.story["lineupByTrust"]:
		if people.relation("coach") >= int(pair[0]):
			player.lineup_slot = int(pair[1])
			break


## 다음 시즌: 학년이 오르고 기록·순위는 새로, 학교들은 졸업·전력 변화, 라이벌·유망주도 한 해 자란다.
## 타순은 감독 신뢰로 새로 정한다 (career.story.lineupByTrust)
func start_next_season() -> void:
	history.append({"season_no": season_no, "grade": player.grade, "line": player.season, "phases": phase_log.duplicate(),
		"record": "%d승 %d패 %d무" % [wins(), losses(), results.count(0)], "rank": prospect_rank()})
	rival.new_season(cfg)
	scouting.winter(cfg)
	var carry := float(cfg.scout["seasonCarry"])
	player.scout_interest = roundi(player.scout_interest * carry)
	rival.player.scout_interest = roundi(rival.player.scout_interest * carry)
	_lineup_by_trust()
	# 졸업하는 고정 인물의 자리는 새 선수가 채운다
	for id: String in cfg.people:
		var l: Dictionary = cfg.people[id].get("lineup", {})
		if not l.is_empty() and player.grade == int(l.get("untilGrade", 3)):
			my_school.team.replace_batter(int(l["slot"]) - 1, my_school.strength, cfg, _rng("graduate-" + id))
	season_no += 1
	game_done = false
	winter_gains = {}
	camp_week = 0
	camp_focus = ""
	camp_games.clear()
	camp_start.clear()
	player.grade = mini(player.grade + 1, 3)
	player.season = PlayerData.SeasonStats.new()
	player.mark_season_start()
	player.condition = maxi(player.condition, int(cfg.start["condition"]))
	player.injury_weeks = 0
	player.injury_label = ""
	player.form = 0.0
	results.clear()
	phase_log.clear()
	week = 1
	week_training = ""
	week_schedule = []
	_week_training_id = ""
	week_social = ""
	week_services.clear()
	phase_index = 0
	var rng := _rng("newseason")
	my_school.new_season(cfg, rng)
	for id: String in schools:
		schools[id].new_season(cfg, rng)
	_start_phase()
	phone.deliver(self)


## 화면이 쓰는 시드 난수 (미니게임 신호 시간 등 — 같은 커리어·같은 주면 같은 값)
func rng_for(salt: String) -> RandomNumberGenerator:
	return _rng(salt)


## 옛 저장을 불러온 뒤 (SaveGame 이 부른다): 그 뒤에 생긴 능력치(2026-11-01 수비)의 잠재력·시즌 시작·캠프 시작 값을 채운다.
## 새 능력치 값은 변수 기본값으로 시작하고, 잠재력은 시드로 뽑는다(라이벌은 설정값). 이미 다 있으면 아무 일도 없다
func upgrade_old_save() -> void:
	player.fill_missing_stats(cfg, _rng("upgrade-stats"))
	# 연락처가 생기기 전 저장: 만난 사람을 모두 연락처로
	if phone.contacts.is_empty():
		for id: String in people.by_id:
			if people.is_met(id):
				phone.add_contact(id)
	if rival != null:
		rival.player.fill_missing_stats(cfg, _rng("upgrade-rival"), cfg.rival["potential"])
	if not camp_start.is_empty():
		for s in PlayerData.STATS:
			if not camp_start.has(s):
				camp_start[s] = player.stat(s)


func _rng(salt: String) -> RandomNumberGenerator:
	var r := RandomNumberGenerator.new()
	r.seed = _seed(salt)
	return r


## 캠프 중엔 시즌 주차가 멈추므로 캠프 주차를 섞는다 (시즌 중 시드는 그대로)
func _seed(salt: String) -> int:
	return hash([cfg.seed, season_no, week, salt if camp_week == 0 else "%s@camp%d" % [salt, camp_week]])
