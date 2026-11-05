class_name ProFeed
extends RefCounted
## 프로 편 뉴스·SNS. 한 주가 끝나면 이번 주 리그 사건(ProLeague.week_events)과 내 경기 기록으로 기사·SNS 글을 만든다.
## 글은 content.json pro.news / pro.sns 의 문장 틀에서 고른다 ({player} {team} {n} … 치환). 글자 고르기는 시드 난수 (원칙 2).
## 기사는 매체(pro.media.news)를, SNS 글은 팬 아이디(pro.sns.handles)·좋아요 수(인기도로)·댓글을 단다.

## [{"kind": "news"|"sns", "type": 사건 종류, "source", "title", "body", "likes", "comments": [글자], "year", "week", "read"}] 최근 것이 앞
var items: Array = []


func unread_count() -> int:
	var n := 0
	for it: Dictionary in items:
		if not it["read"]:
			n += 1
	return n


func mark_all_read() -> void:
	for it: Dictionary in items:
		it["read"] = true


func news() -> Array:
	return items.filter(func(it: Dictionary) -> bool: return it["kind"] == "news")


func sns() -> Array:
	return items.filter(func(it: Dictionary) -> bool: return it["kind"] == "sns")


## 문장 틀을 마지막으로 쓴 순서 (틀 글자 → 번호). 안 쓴 틀·오래된 틀부터 고른다 — 같은 문구를 되도록 다시 안 보게
var used := {}
var _clock := 0


## 한 주 끝 (ProCareer 의 한 주 정리가 부른다). 한 주에 feedPerWeek 개까지:
## 내 한 주 · 내 경기 한 장면(홈런·맹타·삼진쇼) · 리그 사건(콜업·기념 순간·연승·가을야구…) · 어그로 기사(지금 내 상황에 맞는 소문) ·
## 부문 선두 둘 · 강태오 · 다른 구단 소식 · 순위 · 팀 성적 반응 · 팬 수다
func after_week(career: CareerState, pro: ProCareer, lines: Array) -> void:
	var cfg := career.cfg
	var rng := career.rng_for("pro-feed")
	var made: Array = []
	var line := PlayerData.SeasonStats.new()
	for ln in lines:
		if ln != null:
			line.merge(ln)
	var base := _tokens(career, pro)
	base["line"] = line.line_text()
	base["week"] = str(pro.league.weeks_done)
	# 내 이번 주
	var avg := float(line.hits) / line.ab if line.ab > 0 else 0.0
	if line.pa > 0:
		if line.home_runs > 0 and pro.is_first():
			made.append(_news(cfg, rng, "my_hr", base.merged({"n": str(pro.me.season.home_runs)})))
			made.append(_sns(cfg, rng, pro, "my_hr", base))
		elif avg >= 0.35 and line.ab >= 8:
			made.append(_news(cfg, rng, "my_hot" if pro.is_first() else "futures_hot", base))
			made.append(_sns(cfg, rng, pro, "my_good", base))
		elif avg < 0.15 and line.ab >= 10:
			made.append(_news(cfg, rng, "my_cold", base))
			made.append(_sns(cfg, rng, pro, "my_bad", base))
	# 내 경기 한 장면 (이번 주 가장 눈에 띈 경기 하나)
	var scene := _best_game(pro)
	if not scene.is_empty():
		made.append(_news(cfg, rng, scene["type"], base.merged(scene["tokens"])))
	# 리그 사건
	for ev: Dictionary in pro.league.week_events:
		var t := base.merged({})
		for k: String in ev:
			t[k] = str(ev[k])
		match ev["kind"]:
			"callup", "demote", "national", "enlist", "discharge", "rival_retire":
				made.append(_news(cfg, rng, ev["kind"], t))
				made.append(_sns(cfg, rng, pro, ev["kind"], t))
			"milestone":
				made.append(_news(cfg, rng, "milestone", t))
				made.append(_sns(cfg, rng, pro, "milestone", t))
			"streak_win", "streak_loss", "post", "champion", "award":
				made.append(_news(cfg, rng, ev["kind"], t))
	# 어그로 기사 (주마다 하나, 가끔 둘)
	made.append(_gossip(career, pro, rng, base, avg, line.ab))
	if rng.randf() < float(cfg.pro["gossipSecondChance"]):
		made.append(_gossip(career, pro, rng, base, avg, line.ab))
	if pro.phase == ProCareer.PHASE_REGULAR or pro.league.weeks_done == int(cfg.pro["weeks"]):
		# 부문 선두 둘 (주마다 부문을 돌린다)
		var stats := ["avg", "hr", "rbi", "era", "so", "sb", "hits", "wins"]
		for k in 2:
			var stat: String = stats[(pro.league.weeks_done * 2 + k) % stats.size()]
			var top := pro.league.leaders(cfg, stat, 1)
			if not top.is_empty():
				var p: ProPlayer = top[0]["player"]
				made.append(_news(cfg, rng, "leader", base.merged({"stat": cfg.pro_content["statNames"][stat], "name": p.name,
					"leaderTeam": pro.league.team(p.team_id).name, "text": top[0]["text"]})))
		# 강태오 소식
		var rv := pro.league.rival_player()
		if rv != null and rv.season.pa > 0 and pro.league.weeks_done % 2 == 0:
			made.append(_news(cfg, rng, "rival", base.merged({"season": rv.line_text()})))
		# 다른 구단 소식 (리그 선수 하나 — 트레이드설·깜짝 활약·부진)
		var others := pro.league.all_batters().filter(func(q: ProPlayer) -> bool: return q.team_id != pro.team_id and not q.is_rival and q.season.pa > 0)
		if not others.is_empty():
			var star: ProPlayer = others[rng.randi_range(0, others.size() - 1)]
			made.append(_news(cfg, rng, "other_team", base.merged({"star": star.name, "starTeam": pro.league.team(star.team_id).name,
				"text": star.line_text()})))
		# 순위 (2주마다)
		if pro.league.weeks_done % 2 == 0 and pro.league.weeks_done > 0:
			var rows := pro.league.standings()
			made.append(_news(cfg, rng, "standings", base.merged({"first": (rows[0] as ProTeam).name, "rank": str(pro.league.rank_of(pro.team_id))})))
	# 팀 성적 반응 · 팬 수다 (2026-11-05 전수 점검: 하루 단위로 바뀐 뒤 week_games 줄에 result 가 없어 여기서 멈췄다 → team 안의 결과)
	var r := 0
	for g: Dictionary in pro.week_games:
		r += int((g.get("team", {}) as Dictionary).get("result", 0))
	if not pro.week_games.is_empty():
		made.append(_sns(cfg, rng, pro, "team_win" if r > 0 else "team_loss" if r < 0 else "team_even", base))
	for k in int(cfg.pro["fanTalkPerWeek"]):
		made.append(_sns(cfg, rng, pro, "fan_talk", base))
	_push(career, pro, made, int(cfg.pro["feedPerWeek"]))


## 이번 주 내 1군 경기 중 기사 거리 하나: 홈런 2개 · 3안타 이상 · 홈런 · 3삼진 이상 (없으면 {})
func _best_game(pro: ProCareer) -> Dictionary:
	var best := {}
	var score := 0
	for g: Dictionary in pro.week_games:
		var ln: PlayerData.SeasonStats = g.get("line", null)
		var t: Dictionary = g.get("team", {})
		if ln == null or t.is_empty() or (g.get("mine", {}) as Dictionary).get("level", "") != ProCareer.LEVEL_FIRST:
			continue
		var type := ""
		var sc := 0
		if ln.home_runs >= 2:
			type = "game_multi_hr"
			sc = 5
		elif ln.hits >= 3:
			type = "game_three_hits"
			sc = 4
		elif ln.home_runs == 1:
			type = "game_hr"
			sc = 3
		elif ln.strikeouts >= 3:
			type = "game_strikeouts"
			sc = 2
		if sc > score:
			score = sc
			best = {"type": type, "tokens": {"opp": t["opp"], "line": ln.line_text(), "score": "%d:%d" % [t["my"], t["their"]],
				"result": ["패", "무", "승"][int(t["result"]) + 1]}}
	return best


## 어그로 기사: 지금 내 상황에 맞는 소문 주제 하나를 골라 [머리말] + 본문 + 꼬리 (content.json pro.gossip — 조합이라 같은 제목이 드물다)
func _gossip(career: CareerState, pro: ProCareer, rng: RandomNumberGenerator, base: Dictionary, avg: float, ab: int) -> Dictionary:
	var cfg := career.cfg
	var gc: Dictionary = cfg.pro_content["gossip"]
	var tc: Dictionary = cfg.pro["trust"]
	var topics := ["generic"]
	if ab >= 8 and avg < 0.2:
		topics.append("slump")
	if ab >= 8 and avg >= 0.35:
		topics.append("hot")
	if career.people.partner != "":
		topics.append("romance")
	if career.people.is_met("chae") and career.people.partner != "chae":
		topics.append("chae")
	if pro.is_first() and pro.trust < float(tc["demoteBelow"]) + 6.0:
		topics.append("coach")
	if pro.level == ProCareer.LEVEL_FUTURES and pro.debuted:
		topics.append("futures")
	if pro.fa_seasons >= ProFA.required(cfg, pro) - 1 and pro.contract_years_left <= 1:
		topics.append("fa")
	if pro.league.rival_player() != null:
		topics.append("rival")
	if pro.salary >= 100000000 or pro.bonus >= 100000000:
		topics.append("money")
	if pro.fame >= 15:
		topics.append("restaurant")
	if pro.military == ProService.NONE and pro.me.age >= 24:
		topics.append("military")
	var topic: String = topics[rng.randi_range(0, topics.size() - 1)]
	var t := base.merged({"hint": (gc["hints"] as Dictionary).get(career.people.partner, "지인 A씨"), "coach": _coach_name(cfg, pro)})
	var core := fill(_pick(gc["topics"][topic], rng), t)
	var prefix := _pick(gc["prefixes"], rng)
	var suffix := _pick(gc["suffixes"], rng)
	var title := (prefix + " " if prefix != "" else "") + core + suffix
	var media: Array = gc["media"]
	var comments := _pick_many(gc["comments"], rng, int(cfg.pro["snsComments"]), t)
	return {"kind": "news", "type": "gossip", "source": media[rng.randi_range(0, media.size() - 1)], "title": title, "body": "",
		"likes": 0, "comments": comments}


func _coach_name(cfg: CareerConfig, pro: ProCareer) -> String:
	for d: Dictionary in cfg.pro_teams:
		if d.get("id", "") == pro.team_id:
			return str(d.get("coach", "감독"))
	return "감독"


## 시즌 결산 (상·연봉)
func after_season(career: CareerState, pro: ProCareer) -> void:
	var cfg := career.cfg
	var rng := career.rng_for("pro-feed-season")
	var base := _tokens(career, pro)
	var made: Array = []
	for a: String in pro.season_awards:
		made.append(_news(cfg, rng, "my_award", base.merged({"award": a})))
		made.append(_sns(cfg, rng, pro, "my_award", base.merged({"award": a})))
	made.append(_news(cfg, rng, "salary", base.merged({"salary": ProCareer.money_text(pro.salary)})))
	# 리그 큰 상 (MVP · 신인왕 · 한국시리즈 MVP) 과 우승 팀
	for ev: Dictionary in pro.league.week_events:
		if ev["kind"] == "award" and ev["award"] in ["MVP", "신인왕", "한국시리즈 MVP"]:
			made.append(_news(cfg, rng, "award", base.merged({"award": ev["award"], "name": ev["name"], "team": ev["team"]})))
	_push(career, pro, made, 99)
	pro.league.week_events = []


## 엔진이 직접 올리는 기사 (예: 은퇴) — 주가 더 흐르지 않을 때도 남긴다
func post_news(career: CareerState, pro: ProCareer, type: String, extra: Dictionary = {}) -> void:
	var rng := career.rng_for("pro-news-" + type)
	_push(career, pro, [_news(career.cfg, rng, type, _tokens(career, pro).merged(extra))], 99)


## 이야기·문자가 직접 올리는 글 (예: 히로인의 SNS 사진) — 효과 proSns 가 부른다
func post_custom(career: CareerState, pro: ProCareer, type: String, extra: Dictionary = {}) -> void:
	var rng := career.rng_for("pro-feed-" + type)
	_push(career, pro, [_sns(career.cfg, rng, pro, type, _tokens(career, pro).merged(extra))], 99)


func _push(career: CareerState, pro: ProCareer, made: Array, cap: int) -> void:
	var n := 0
	for it in made:
		if it == null or (it as Dictionary).is_empty():
			continue
		if n >= cap:
			break
		it["year"] = pro.league.year
		it["week"] = career.total_week
		it["read"] = false
		items.push_front(it)
		n += 1
	while items.size() > int(career.cfg.pro["feedKeep"]):
		items.pop_back()


func _tokens(career: CareerState, pro: ProCareer) -> Dictionary:
	var t := pro.team()
	var rv := pro.league.rival_player()
	return {"player": career.player.name, "team": t.name, "teamShort": t.short, "city": t.city,
		"season": pro.me.line_text(), "rival": rv.name if rv != null else "강태오", "rivalTeam": pro.league.team(rv.team_id).name if rv != null else "",
		"year": str(pro.league.year), "rank": str(pro.league.rank_of(pro.team_id))}


func _news(cfg: CareerConfig, rng: RandomNumberGenerator, type: String, tokens: Dictionary) -> Dictionary:
	var pool: Array = cfg.pro_content["news"].get(type, [])
	if pool.is_empty():
		return {}
	var media: Array = cfg.pro_content["media"]["news"]
	return {"kind": "news", "type": type, "source": media[rng.randi_range(0, media.size() - 1)],
		"title": fill(_pick(pool, rng), tokens), "body": "", "likes": 0, "comments": []}


func _sns(cfg: CareerConfig, rng: RandomNumberGenerator, pro: ProCareer, type: String, tokens: Dictionary) -> Dictionary:
	var sc: Dictionary = cfg.pro_content["sns"]
	var pool: Array = sc["posts"].get(type, [])
	if pool.is_empty():
		return {}
	var handles: Array = sc["handles"]
	# 댓글: 그 종류의 댓글 + 아무 글에나 붙는 댓글을 섞어서, 최근에 안 쓴 것부터
	var comments: Array = (sc["comments"].get(type, []) as Array) + (sc["comments"]["_"] as Array)
	var picked := _pick_many(comments, rng, int(cfg.pro["snsComments"]), tokens)
	# 이야기 인물이 올리는 글은 그 사람 아이디 (pro.sns.postHandles)
	var source: String = sc.get("postHandles", {}).get(type, handles[rng.randi_range(0, handles.size() - 1)])
	var likes := int(cfg.pro["snsLikesBase"]) + pro.fame * int(cfg.pro["snsLikesPerFame"]) + rng.randi_range(0, int(cfg.pro["snsLikesBase"]))
	return {"kind": "sns", "type": type, "source": source, "title": fill(_pick(pool, rng), tokens),
		"body": "", "likes": likes, "comments": picked}


## 틀 하나: 한 번도 안 쓴 틀 → 가장 오래전에 쓴 틀 순으로 줄 세워 앞쪽 몇 개에서 시드 난수로 (같은 문구를 되도록 다시 안 보게)
func _pick(pool: Array, rng: RandomNumberGenerator) -> String:
	if pool.is_empty():
		return ""
	var order := pool.duplicate()
	order.sort_custom(func(a: String, b: String) -> bool:
		var ua := int(used.get(a, -1))
		var ub := int(used.get(b, -1))
		if ua != ub:
			return ua < ub
		return pool.find(a) < pool.find(b))
	var fresh := mini(3, order.size())
	var choice: String = order[rng.randi_range(0, fresh - 1)]
	used[choice] = _clock
	_clock += 1
	return choice


## 서로 다른 n 개 (댓글), 최근에 안 쓴 것부터
func _pick_many(pool: Array, rng: RandomNumberGenerator, n: int, tokens: Dictionary) -> Array:
	var out := []
	var left := pool.duplicate()
	for i in mini(n, pool.size()):
		var c := _pick(left, rng)
		left.erase(c)
		out.append(fill(c, tokens))
	return out


## {키} 치환 + 조사 맞추기
static func fill(text: String, tokens: Dictionary) -> String:
	var t := text
	for k: String in tokens:
		t = t.replace("{%s}" % k, str(tokens[k]))
	return EventBook.josa(t)


## 문장 틀 검사: 모든 틀의 {키} 가 알려진 키인가
static func validate(cfg: CareerConfig) -> Array[String]:
	var errors: Array[String] = []
	var known := ["player", "team", "teamShort", "city", "season", "rival", "rivalTeam", "year", "rank", "line", "week", "n", "stat", "name",
		"leaderTeam", "text", "first", "round", "winner", "loser", "wins", "award", "salary", "title", "id", "kind", "handle",
		"opp", "score", "result", "star", "starTeam", "hint", "coach"]
	var pc: Dictionary = cfg.pro_content
	for k in ["news", "sns", "media", "milestones", "statNames"]:
		if not pc.has(k):
			errors.append("content.json pro.%s 가 없다" % k)
	if not errors.is_empty():
		return errors
	var texts: Array = []
	for type: String in pc["news"]:
		if not type.begins_with("_"):
			texts.append_array(pc["news"][type])
	for type: String in pc["sns"]["posts"]:
		texts.append_array(pc["sns"]["posts"][type])
	for type: String in pc["sns"]["comments"]:
		texts.append_array(pc["sns"]["comments"][type])
	if pc.has("gossip"):
		for topic: String in pc["gossip"]["topics"]:
			texts.append_array(pc["gossip"]["topics"][topic])
		texts.append_array(pc["gossip"]["comments"])
	else:
		errors.append("content.json pro.gossip 이 없다")
	for t: String in texts:
		for tok in EventBook._tokens(t):
			if not tok in known:
				errors.append("뉴스·SNS 틀 '%s': 모르는 치환 {%s}" % [t, tok])
	return errors
