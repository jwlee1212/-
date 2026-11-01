class_name NewsFeed
extends RefCounted
## 폰 뉴스 (content.json news, 형식은 news._note). 로직만 — 화면은 PhoneView 의 뉴스 탭이 그린다.
##
## 기사는 손으로 쓴 글이 아니라 "문장 틀 + 실제 기록"으로 만든다. 공식 경기가 끝날 때마다(after_game)
## 그 경기에서 눈에 띄는 일(홈런·맹타·연속 안타·무안타·대회 진출·라이벌·스카우트·월간 파워랭킹·시즌 결산)을 후보로 모으고,
## 중요한 순서로 한 주에 maxPerWeek 건까지 싣는다. 이야기 장면의 "뉴스 알림" 줄은 속보 기사로 바로 실린다 (add_story).
##
## 매체는 유명한 정도(tier)에 따라 올라간다 — 미디어 단계 (docs/worldview.md 4-4):
##   0 야구 커뮤니티 게시글 → 1 아마야구 전문 매체 기사 → 2 일간 스포츠 매체 기사.
##   tier 는 스카우트 관심도·유망주 랭킹·전국대회 무대로 정한다 (balance.json career.news.tiers).
## 문장 틀·제목·문단 변형·댓글은 커리어 시드 난수로 고른다 (원칙 2) — 같은 진행이면 같은 기사다.
## 관계·능력치는 바꾸지 않는다. 읽기만 하는 곳이다 (매주 처리할 결정이 늘지 않는다).

## 기사 종류 (content.json news.kinds 의 키). 앞에 있을수록 한 주 자리 경쟁에서 먼저 실린다
const KINDS := ["story", "champion", "season_end", "first_homer", "homer", "rival_duel", "big_game", "first_hit",
	"team_advance", "team_out", "streak", "scout_watch", "power_ranking", "league_end", "rival_big", "hitless", "slump", "good_game"]
## tier 별 기본 매체 (틀에 source 가 있으면 그것)
const TIER_SOURCES := ["board", "report", "daily"]
## 연속 안타가 이 경기 수에 닿을 때 기사가 된다
const STREAK_MARKS := [5, 8, 10, 12, 15, 20, 25, 30]
## 문단에 붙일 수 있는 조건 (ctx 가 켜 주는 이름)
const FLAGS := ["win", "loss", "draw", "home", "away", "national", "league", "ranked", "unranked", "rankUp", "rankDown",
	"metRival", "multiHr", "walkOff", "hasRbi", "noRbi", "hasBb", "hasSteal", "grade1", "grade2", "grade3", "starter", "pinch",
	"hasHr", "noHr", "bigRbi", "threeHits", "gotHit", "noHit", "rivalHr", "rivalNoHr"]

## 실린 기사 (오래된 것이 앞). 한 기사:
##   {"id", "tw", "grade", "week", "month", "kind", "source", "title", "body": [문단…], "author", "views", "up", "comments": [{"nick", "text", "likes"}], "read"}
var articles: Array = []
## 아직 허브에서 알리지 않은 새 기사 id
var fresh: Array[int] = []
var _seq := 0
## 기사로 다룬 연속 안타 기록 (같은 숫자를 두 번 쓰지 않는다)
var _streak_done := 0
## 파워랭킹을 낸 달 ("시즌번호-월")
var _ranking_done := {}
## 마지막 라이벌 기사를 낸 주 (total_week, 너무 잦지 않게)
var _rival_tw := -99


# ---------- 싣기 ----------

## 공식 경기 뒤 (CareerState.finish_game 끝에서). entry = 이 경기의 기록실 한 줄(대회 이름·라운드·상대·점수),
## phase_end = 단계가 어떻게 끝났나, teams_before = 경기 전 지켜보던 구단 수
func after_game(career: CareerState, game: GameRunner, entry: CareerRecords.GameEntry, phase_end: String, teams_before: int) -> void:
	var ctx := _game_ctx(career, game, entry, phase_end)
	var tier := _tier(career, entry)
	var cands := _candidates(career, game, entry, phase_end, teams_before, ctx, tier)
	var cap := int(career.cfg.news_cfg["maxPerWeek"])
	cands.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return KINDS.find(a["kind"]) < KINDS.find(b["kind"]))
	for c: Dictionary in cands.slice(0, cap):
		publish(career, c["kind"], int(c["tier"]), ctx)


## 이야기 장면의 뉴스 알림 줄 → 속보 기사 (일간 매체)
func add_story(career: CareerState, headline: String) -> void:
	var ctx := _base_ctx(career)
	ctx["headline"] = headline.trim_suffix(".")
	publish(career, "story", 2, ctx)


## 기사 하나를 만들어 싣는다. 돌려주는 값: 그 기사
func publish(career: CareerState, kind: String, tier: int, ctx: Dictionary) -> Dictionary:
	var news := career.cfg.news
	var def: Dictionary = news["kinds"][kind]
	_seq += 1
	var rng := career._rng("news:%d:%s" % [_seq, kind])
	var tiers: Dictionary = def["tiers"]
	# 그 단계 틀이 없으면 아래 단계, 아래에도 없으면 위 단계 (우승 기사는 일간 매체 틀뿐이다)
	var t := tier
	while t > 0 and not tiers.has(str(t)):
		t -= 1
	while t < 2 and not tiers.has(str(t)):
		t += 1
	var tmpl: Dictionary = _pick(tiers[str(t)], rng)
	var source: String = tmpl.get("source", TIER_SOURCES[t])
	var src: Dictionary = news["sources"][source]
	var flags: Dictionary = ctx.get("_flags", {})
	# 문구 묶음({quote} {scoutSays} …)은 기사마다 새로 고른다
	for key: String in news["phrases"]:
		if not key.begins_with("_"):
			ctx[key] = _pick(news["phrases"][key], rng)
	var body: Array[String] = []
	var title: String = fill(career, ctx, _pick(usable(tmpl["title"], flags), rng))
	for para in tmpl["body"]:
		if para is Dictionary and not _flag_ok(flags, para["when"]):
			continue
		var v := usable(para if para is Array else para["v"], flags)
		if v.is_empty():
			continue
		var line := fill(career, ctx, _pick(v, rng))
		if line != "":
			body.append(line)
	var community: bool = src.get("community", false)
	var a := {"id": _seq, "tw": career.total_week, "grade": career.player.grade, "week": career.week, "month": int(ctx["month"]),
		"kind": kind, "source": source, "title": title, "body": body,
		"author": nick(news, rng) if community else _pick(src["reporters"], rng) + " 기자",
		"views": rng.randi_range(int(src["views"][0]), int(src["views"][1])) * (t + 1),
		"up": rng.randi_range(0, 40 * (t + 1)), "comments": _comments(career, def, ctx, rng, t), "read": false}
	articles.append(a)
	fresh.append(a["id"])
	var keep := int(career.cfg.news_cfg["keep"])
	if articles.size() > keep:
		articles = articles.slice(articles.size() - keep)
	return a


func _comments(career: CareerState, def: Dictionary, ctx: Dictionary, rng: RandomNumberGenerator, tier: int) -> Array:
	var news := career.cfg.news
	var pool: Array = []
	for set_id: String in def.get("comments", []):
		pool.append_array(usable(news["comments"][set_id], ctx.get("_flags", {})))
	var out := []
	if pool.is_empty():
		return out
	var count_range: Array = career.cfg.news_cfg["comments"]
	var n := mini(rng.randi_range(int(count_range[0]), int(count_range[1])), pool.size())
	var bag := pool.duplicate()
	for i in n:
		var k := rng.randi_range(0, bag.size() - 1)
		var text: String = bag[k]
		bag.remove_at(k)
		out.append({"nick": nick(news, rng), "text": fill(career, ctx, text), "likes": int(rng.randi_range(0, 60) * (tier + 1) / (i + 1))})
	out.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a["likes"] > b["likes"])
	return out


## 댓글·게시글 닉네임: 앞말 + 뒷말 (가끔 숫자)
static func nick(news: Dictionary, rng: RandomNumberGenerator) -> String:
	var parts: Dictionary = news["nicks"]
	var n: String = _pick(parts["head"], rng) + _pick(parts["tail"], rng)
	if rng.randf() < 0.3:
		n += str(rng.randi_range(1, 99))
	return n


# ---------- 후보 고르기 ----------

## 유명한 정도: 0 커뮤니티 · 1 전문 매체 · 2 일간 매체
func _tier(career: CareerState, entry: CareerRecords.GameEntry) -> int:
	var t: Dictionary = career.cfg.news_cfg["tiers"]
	var scout := career.player.scout_interest
	var rank := career.prospect_rank()
	var big_stage := entry.national and entry.round in (t["bigRounds"] as Array)
	if scout >= int(t["dailyScout"]) or rank <= int(t["dailyRank"]) or big_stage:
		return 2
	if scout >= int(t["reportScout"]) or rank <= int(t["reportRank"]) or entry.national:
		return 1
	return 0


func _candidates(career: CareerState, game: GameRunner, entry: CareerRecords.GameEntry, phase_end: String, teams_before: int, ctx: Dictionary, tier: int) -> Array:
	var c := career.cfg.news_cfg
	var rng := career._rng("news-pick")
	var out := []
	var add := func(kind: String, t: int) -> void: out.append({"kind": kind, "tier": clampi(t, 0, 2)})
	var line := game.my_line
	var played := game.me_playing and line.pa > 0
	var before := _career_before(career, entry)
	if played:
		if line.home_runs > 0 and before["hr"] == 0:
			add.call("first_homer", maxi(tier, 1))
		elif line.home_runs > 0:
			add.call("homer", tier)
		# 홈런이 있으면 홈런 기사가 그 경기를 다룬다 (같은 경기 기사 두 건 방지)
		if line.home_runs == 0 and (line.hits >= 3 or line.rbi >= 4):
			add.call("big_game", tier)
		elif line.hits == 2 and rng.randf() < float(c["goodGameChance"]):
			add.call("good_game", 0)
		if line.hits > 0 and before["hits"] == 0:
			add.call("first_hit", 0)
		var streak := hit_streak(career)
		if streak in STREAK_MARKS and streak > _streak_done:
			_streak_done = streak
			ctx["streak"] = str(streak)
			add.call("streak", tier)
		if streak == 0:
			_streak_done = 0
		var dry := hitless_run(career)
		# 무안타·부진 글은 이미 이름이 알려진 뒤에만 (아무도 모르는 선수의 부진은 글이 안 된다)
		if tier >= 1 and dry >= int(c["hitlessGames"]):
			ctx["dry"] = str(dry)
			add.call("hitless", 0)
		elif tier >= 1 and line.hits == 0 and line.strikeouts >= 2 and rng.randf() < float(c["slumpChance"]):
			add.call("slump", 0)
	# 팀 기사: 작은 학교의 16강·8강은 일간 매체까지 가지 않는다 (준결승·결승만)
	var team_tier := tier if entry.round in (c["tiers"]["bigRounds"] as Array) else mini(tier, 1)
	if entry.national:
		if phase_end == "champion":
			add.call("champion", 2)
		elif phase_end == "eliminated":
			add.call("team_out", team_tier)
		elif entry.result > 0:
			add.call("team_advance", team_tier)
	elif phase_end == "leagueEnd" and int(ctx["leagueRank"]) <= int(c["leagueRankNews"]):
		add.call("league_end", 0)
	if career.people.is_met("rival") and career.rival.last_line != null:
		var rl := career.rival.last_line
		if career.rival.last_vs_me and played:
			add.call("rival_duel", maxi(tier, 1))
		elif (rl.home_runs > 0 or rl.hits >= 3) and career.total_week - _rival_tw >= int(c["rivalCooldownWeeks"]):
			if rng.randf() < float(c["rivalChance"]):
				_rival_tw = career.total_week
				add.call("rival_big", 1)
	var teams := career.scouting.interested_teams(career.cfg, career.player.scout_interest, career.player.grade)
	if teams.size() > teams_before:
		ctx["scoutTeam"] = teams[-1]["name"]
		add.call("scout_watch", maxi(tier, 1))
	var key := "%d-%d" % [career.season_no, int(ctx["month"])]
	if career.week % int(c["rankingEveryWeeks"]) == 0 and not _ranking_done.has(key):
		_ranking_done[key] = true
		add.call("power_ranking", 1)
	if career.is_season_over() and career.player.season.pa > 0:
		add.call("season_end", tier)
	return out


## 이 경기 전까지의 고교 통산 안타·홈런 (첫 안타·첫 홈런 기사용)
func _career_before(career: CareerState, entry: CareerRecords.GameEntry) -> Dictionary:
	var total := career.career_line()
	return {"hits": total.hits - entry.line.hits, "hr": total.home_runs - entry.line.home_runs}


## 이번 시즌 연속 안타 경기 (최근 경기부터 거꾸로, 나오지 않은 경기·타수 없는 경기는 건너뛴다)
static func hit_streak(career: CareerState) -> int:
	var n := 0
	for i in range(career.records.games.size() - 1, -1, -1):
		var g: CareerRecords.GameEntry = career.records.games[i]
		if g.season_no != career.season_no:
			break
		if not g.playing or g.line.ab == 0:
			continue
		if g.line.hits == 0:
			break
		n += 1
	return n


## 이번 시즌 연속 무안타 경기 (타수가 있는 경기만)
static func hitless_run(career: CareerState) -> int:
	var n := 0
	for i in range(career.records.games.size() - 1, -1, -1):
		var g: CareerRecords.GameEntry = career.records.games[i]
		if g.season_no != career.season_no:
			break
		if not g.playing or g.line.ab == 0:
			continue
		if g.line.hits > 0:
			break
		n += 1
	return n


# ---------- 글자 채우기 ----------

## 어느 기사에나 쓰는 값
func _base_ctx(career: CareerState) -> Dictionary:
	var p := career.player
	var st := p.season
	var ctx := {"school": career.my_school.name, "grade": str(p.grade), "pos": career.cfg.news["position"],
		"month": str(month_of(career)), "week": str(career.week),
		"seasonAvg": st.average(), "seasonOps": st.ops(), "seasonObp": st.obp_text(), "seasonHr": str(st.home_runs), "seasonHits": str(st.hits),
		"seasonRbi": str(st.rbi), "seasonGames": str(st.games), "seasonBb": str(st.walks), "seasonSb": str(st.steals),
		"careerHr": str(career.career_line().home_runs), "careerHits": str(career.career_line().hits),
		"rank": str(career.prospect_rank()), "scoutInterest": str(p.scout_interest),
		"teamCount": str(career.scouting.interested_teams(career.cfg, p.scout_interest, p.grade).size()),
		"rivalSchool": career.schools[career.rival.school_id].name if career.schools.has(career.rival.school_id) else "",
		"rivalAvg": career.rival.player.season.average(), "rivalHr": str(career.rival.player.season.home_runs),
		"rivalLine": career.rival.last_line.line_text() if career.rival.last_line != null else "",
		"scoutTeam": "", "streak": "", "dry": "", "headline": "", "leagueRank": "0"}
	var rank_move := -Scouting.delta(career.scouting.rank_history, int(career.cfg.scouting["deltaWeeks"]))
	ctx["rankMove"] = str(absi(rank_move))
	var rl := career.rival.last_line
	var flags := {"metRival": career.people.is_met("rival"), "rivalHr": rl != null and rl.home_runs > 0, "rivalNoHr": rl == null or rl.home_runs == 0, "ranked": career.prospect_rank() <= int(career.cfg.news_cfg["rankedTop"]),
		"rankUp": rank_move > 0, "rankDown": rank_move < 0, "grade1": p.grade == 1, "grade2": p.grade == 2, "grade3": p.grade == 3}
	flags["unranked"] = not flags["ranked"]
	ctx["_flags"] = flags
	_ranking_ctx(career, ctx)
	return ctx


func _game_ctx(career: CareerState, game: GameRunner, entry: CareerRecords.GameEntry, phase_end: String) -> Dictionary:
	var ctx := _base_ctx(career)
	var line := game.my_line
	var flags: Dictionary = ctx["_flags"]
	var words := ["패배", "무승부", "승리"]
	ctx["opp"] = entry.opponent
	ctx["phase"] = entry.phase_name
	ctx["round"] = entry.round
	ctx["score"] = "%d-%d" % [entry.my_score, entry.opp_score]
	ctx["resultWord"] = words[entry.result + 1]
	ctx["line"] = line.line_text() if game.me_playing else "결장"
	for k: String in ["ab", "hits", "rbi", "walks", "strikeouts", "steals", "doubles", "triples"]:
		ctx[k] = str(line.get(k))
	ctx["hr"] = str(line.home_runs)
	ctx["xbh"] = str(line.doubles + line.triples + line.home_runs)
	ctx["hrNo"] = str(career.player.season.home_runs)
	ctx["nextRound"] = ""
	var rounds: Array = career.cfg.season["tournamentRounds"]
	var ri := rounds.find(entry.round)
	if ri >= 0 and ri + 1 < rounds.size():
		ctx["nextRound"] = rounds[ri + 1]
	# 내 홈런 타구: 방향·타구 속도 (가장 빠른 것)
	var best_ev := 0.0
	var spray := 0.0
	for pa: Dictionary in game.my_pas:
		if pa["outcome"] == SwingJudge.Outcome.HOME_RUN and float(pa["ev"]) >= best_ev:
			best_ev = float(pa["ev"])
			spray = float(pa["spray"])
	ctx["hrEv"] = str(roundi(best_ev)) if best_ev > 0.0 else "140"
	ctx["hrDir"] = direction(spray)
	# 최근 5경기
	var recent := PlayerData.SeasonStats.new()
	var n := 0
	for i in range(career.records.games.size() - 1, -1, -1):
		var g: CareerRecords.GameEntry = career.records.games[i]
		if g.season_no != career.season_no or n >= 5:
			break
		if g.playing:
			recent.merge(g.line)
			n += 1
	ctx["recentN"] = str(n)
	ctx["recentAvg"] = recent.average()
	ctx["recentHits"] = str(recent.hits)
	# 주말리그 순위 (리그가 끝난 경기)
	if phase_end == "leagueEnd" and not career.phase_log.is_empty():
		var text: String = career.phase_log[-1]["text"]
		ctx["leagueRank"] = text.get_slice("위", 0)
		ctx["leagueRecord"] = text.get_slice("(", 1).trim_suffix(")")
	flags["win"] = entry.result > 0
	flags["loss"] = entry.result < 0
	flags["draw"] = entry.result == 0
	flags["home"] = entry.home
	flags["away"] = not entry.home
	flags["national"] = entry.national
	flags["league"] = not entry.national
	flags["multiHr"] = line.home_runs >= 2
	flags["hasHr"] = line.home_runs > 0
	flags["noHr"] = line.home_runs == 0
	flags["bigRbi"] = line.rbi >= 3
	flags["threeHits"] = line.hits >= 3
	flags["gotHit"] = line.hits > 0
	flags["noHit"] = line.hits == 0
	flags["hasRbi"] = line.rbi > 0
	flags["noRbi"] = line.rbi == 0
	flags["hasBb"] = line.walks > 0
	flags["hasSteal"] = line.steals > 0
	flags["pinch"] = game.pinch_hit
	flags["starter"] = not game.pinch_hit
	flags["walkOff"] = entry.home and entry.result > 0 and not game.my_pas.is_empty() and int(game.my_pas[-1]["rbi"]) > 0 \
		and game.state.inning >= career.cfg.innings and not game.state.top and _last_pa_ended(game)
	return ctx


## 내 마지막 타석이 경기를 끝냈나 (끝내기): 경기 기록의 마지막 줄이 내 타석이다
static func _last_pa_ended(game: GameRunner) -> bool:
	for i in range(game.log.size() - 1, -1, -1):
		var l: String = game.log[i]
		if l.contains("★"):
			return true
		if l.begins_with("["):
			return false
	return false


## 파워랭킹 표 값: top1~top5 이름·학교
func _ranking_ctx(career: CareerState, ctx: Dictionary) -> void:
	var board := career.prospect_board()
	var ids: Array = career.regional_ids + career.national_ids
	for i in 5:
		var r: Dictionary = board[i] if i < board.size() else {"name": "", "me": false, "rival": false}
		ctx["top%d" % (i + 1)] = r["name"]
		var school := ""
		if r["me"]:
			school = career.my_school.name
		elif r["rival"]:
			school = ctx["rivalSchool"]
		elif not ids.is_empty():
			# 랭킹 속 다른 유망주의 학교: 이름으로 정해지는 학교 (달마다 바뀌지 않게)
			school = career.schools[ids[absi(hash(r["name"])) % ids.size()]].name
		ctx["top%dSchool" % (i + 1)] = school


## 글자 채우기: 기사 값({opp} {line} …) → 인물·내 이름({player} {coach} {rival} …)·조사.
## 문구 묶음 안에 또 기사 값이 들어 있을 수 있어서 두 번 바꾼다
static func fill(career: CareerState, ctx: Dictionary, text: String) -> String:
	var t := text
	for pass_no in 2:
		if not t.contains("{"):
			break
		for k: String in ctx:
			if not k.begins_with("_"):
				t = t.replace("{%s}" % k, str(ctx[k]))
	return josa(EventBook.fill(career, t))


## 이벤트의 조사 고르기(한글 받침)가 못 하는 것: 숫자 뒤("5-3으로", ".333을"), 괄호 뒤("강태오(청운고)가" — 괄호 앞 글자로), (으)로
static func josa(t: String) -> String:
	for pair: Array in [["이(가)", "이", "가"], ["을(를)", "을", "를"], ["은(는)", "은", "는"], ["과(와)", "과", "와"], ["(으)로", "으로", "로"]]:
		var mark: String = pair[0]
		var i := t.find(mark)
		while i > 0:
			var k := i - 1
			if t[k] == ")":
				var open := t.rfind("(", k)
				k = open - 1 if open > 0 else k
			var tail := _tail_sound(t.unicode_at(k))
			var word: String = pair[2] if tail == 0 else pair[1]
			if mark == "(으)로" and tail == 2:
				word = "로"
			t = t.substr(0, i) + word + t.substr(i + mark.length())
			i = t.find(mark, i + 1)
	return t


## 앞 글자의 받침: 0 없음, 1 있음, 2 ㄹ (숫자는 읽는 소리로: 영·일·삼·육·칠·팔 은 받침, 일·칠·팔 은 ㄹ)
static func _tail_sound(code: int) -> int:
	if code >= 0xAC00 and code <= 0xD7A3:
		var jong := (code - 0xAC00) % 28
		return 0 if jong == 0 else (2 if jong == 8 else 1)
	if code >= 48 and code <= 57:
		return [1, 2, 0, 1, 0, 0, 1, 2, 2, 0][code - 48]
	return 0


## 타구 방향 말 (spray: 왼쪽 −, 오른쪽 +, 가운데 0)
static func direction(spray: float) -> String:
	if spray < -27.0:
		return "좌측"
	if spray < -9.0:
		return "좌중간"
	if spray <= 9.0:
		return "가운데"
	if spray <= 27.0:
		return "우중간"
	return "우측"


## 시즌 주차 → 달 (1주차 = 3월, 4주마다 한 달, 10월까지)
static func month_of(career: CareerState) -> int:
	return mini(3 + (career.week - 1) / 4, 10)


static func _flag_ok(flags: Dictionary, when: String) -> bool:
	for f: String in when.split(" "):
		var neg := f.begins_with("!")
		if bool(flags.get(f.trim_prefix("!"), false)) == neg:
			return false
	return true


## 조건이 붙은 변형 풀기: 글자는 그대로, {when, v} 는 조건이 맞을 때만 그 v 들을 넣는다 (제목·댓글)
static func usable(list: Array, flags: Dictionary) -> Array:
	var out := []
	for x in list:
		if x is Dictionary:
			if _flag_ok(flags, x["when"]):
				out.append_array(x["v"])
		else:
			out.append(x)
	return out


static func _pick(list: Array, rng: RandomNumberGenerator) -> Variant:
	return list[rng.randi_range(0, list.size() - 1)]


# ---------- 읽기 ----------

func unread_count() -> int:
	var n := 0
	for a: Dictionary in articles:
		if not a["read"]:
			n += 1
	return n


func mark_read(id: int) -> void:
	for a: Dictionary in articles:
		if a["id"] == id:
			a["read"] = true


func article(id: int) -> Dictionary:
	for a: Dictionary in articles:
		if a["id"] == id:
			return a
	return {}


## 허브 알림용 새 기사들 (부르면 비운다)
func take_fresh() -> Array:
	var out := []
	for id: int in fresh:
		var a := article(id)
		if not a.is_empty():
			out.append(a)
	fresh.clear()
	return out


static func source_name(career: CareerState, source: String) -> String:
	return career.cfg.news["sources"][source]["name"]


# ---------- 무결성 검사 (테스트용) ----------

## 문장 틀 검사: 종류·매체·tier 0, 모르는 글자 치환·조건, 비어 있는 변형
static func validate(cfg: CareerConfig) -> Array[String]:
	var errors: Array[String] = []
	var news := cfg.news
	var texts_extra := []
	var known := EventBook.token_names(cfg)
	known.append_array(["school", "grade", "pos", "month", "week", "seasonAvg", "seasonOps", "seasonObp", "seasonHr", "seasonHits", "seasonRbi",
		"seasonGames", "seasonBb", "seasonSb", "careerHr", "careerHits", "rank", "rankMove", "scoutInterest", "teamCount", "rivalSchool", "rivalAvg",
		"rivalHr", "rivalLine", "scoutTeam", "streak", "dry", "headline", "leagueRank", "leagueRecord", "opp", "phase", "round", "score",
		"resultWord", "line", "ab", "hits", "rbi", "walks", "strikeouts", "steals", "doubles", "triples", "hr", "xbh", "hrNo", "nextRound",
		"hrEv", "hrDir", "recentN", "recentAvg", "recentHits", "quote"])
	for key: String in news["phrases"]:
		known.append(key)
		if not key.begins_with("_"):
			texts_extra.append_array(news["phrases"][key])
	for i in 5:
		known.append("top%d" % (i + 1))
		known.append("top%dSchool" % (i + 1))
	for k: String in KINDS:
		if not (news["kinds"] as Dictionary).has(k):
			errors.append("뉴스 종류 %s 의 문장 틀이 없다" % k)
	for s in TIER_SOURCES:
		if not (news["sources"] as Dictionary).has(s):
			errors.append("매체 %s 없음" % s)
	for sid: String in news["sources"]:
		var src: Dictionary = news["sources"][sid]
		if not src.get("community", false) and (src.get("reporters", []) as Array).is_empty():
			errors.append("매체 %s: reporters 가 비었다" % sid)
	var texts := texts_extra
	for k: String in news["kinds"]:
		if k.begins_with("_"):
			continue
		var def: Dictionary = news["kinds"][k]
		if not def["tiers"].has("0") and k != "story" and k != "champion" and k != "power_ranking" and k != "scout_watch":
			errors.append("뉴스 %s: tier 0 틀이 없다" % k)
		for set_id: String in def.get("comments", []):
			if not news["comments"].has(set_id):
				errors.append("뉴스 %s: 댓글 묶음 %s 없음" % [k, set_id])
		for t: String in def["tiers"]:
			for tmpl: Dictionary in def["tiers"][t]:
				if tmpl.has("source") and not news["sources"].has(tmpl["source"]):
					errors.append("뉴스 %s: 매체 %s 없음" % [k, tmpl["source"]])
				if (tmpl["title"] as Array).is_empty():
					errors.append("뉴스 %s: 제목이 없다" % k)
				if usable(tmpl["title"], {}).is_empty():
					errors.append("뉴스 %s: 조건 없는 제목이 하나는 있어야 한다" % k)
				texts.append_array(_all_variants(tmpl["title"], errors, k))
				for para in tmpl["body"]:
					var v := _all_variants(para if para is Array else para["v"], errors, k)
					if v.is_empty():
						errors.append("뉴스 %s: 빈 문단" % k)
					if para is Dictionary:
						for f: String in str(para["when"]).split(" "):
							if not f.trim_prefix("!") in FLAGS:
								errors.append("뉴스 %s: 모르는 조건 %s" % [k, f])
					texts.append_array(v)
	for set_id: String in news["comments"]:
		texts.append_array(_all_variants(news["comments"][set_id], errors, set_id))
	for t in texts:
		for tok in EventBook._tokens(str(t)):
			if not tok in known:
				errors.append("뉴스: 모르는 글자 치환 {%s} (%s)" % [tok, str(t).left(30)])
	return errors


## 검사용: 조건 붙은 것까지 모든 변형 (조건 이름도 검사한다)
static func _all_variants(list: Array, errors: Array[String], where: String) -> Array:
	var out := []
	for x in list:
		if x is Dictionary:
			for f: String in str(x["when"]).split(" "):
				if not f.trim_prefix("!") in FLAGS:
					errors.append("뉴스 %s: 모르는 조건 %s" % [where, f])
			out.append_array(x["v"])
		else:
			out.append(x)
	return out
