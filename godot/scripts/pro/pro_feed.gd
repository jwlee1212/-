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


## 한 주 끝 (ProCareer.finish_week 이 부른다)
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
	if line.pa > 0:
		var avg := float(line.hits) / line.ab if line.ab > 0 else 0.0
		if line.home_runs > 0 and pro.is_first():
			made.append(_news(cfg, rng, "my_hr", base.merged({"n": str(pro.me.season.home_runs)})))
			made.append(_sns(cfg, rng, pro, "my_hr", base))
		elif avg >= 0.35 and line.ab >= 8:
			made.append(_news(cfg, rng, "my_hot" if pro.is_first() else "futures_hot", base))
			made.append(_sns(cfg, rng, pro, "my_good", base))
		elif avg < 0.15 and line.ab >= 10:
			made.append(_news(cfg, rng, "my_cold", base))
			made.append(_sns(cfg, rng, pro, "my_bad", base))
	# 리그 사건
	for ev: Dictionary in pro.league.week_events:
		var t := base.merged({})
		for k: String in ev:
			t[k] = str(ev[k])
		match ev["kind"]:
			"callup", "demote":
				made.append(_news(cfg, rng, ev["kind"], t))
				made.append(_sns(cfg, rng, pro, ev["kind"], t))
			"milestone":
				made.append(_news(cfg, rng, "milestone", t))
				made.append(_sns(cfg, rng, pro, "milestone", t))
			"streak_win", "streak_loss", "post", "champion", "award":
				made.append(_news(cfg, rng, ev["kind"], t))
	# 리그 선두 하나 (주마다 부문을 돌린다)
	if pro.phase == ProCareer.PHASE_REGULAR or pro.league.weeks_done == int(cfg.pro["weeks"]):
		var stats := ["avg", "hr", "rbi", "era", "so", "sb", "hits", "wins"]
		var stat: String = stats[pro.league.weeks_done % stats.size()]
		var top := pro.league.leaders(cfg, stat, 1)
		if not top.is_empty():
			var p: ProPlayer = top[0]["player"]
			made.append(_news(cfg, rng, "leader", base.merged({"stat": cfg.pro_content["statNames"][stat], "name": p.name,
				"leaderTeam": pro.league.team(p.team_id).name, "text": top[0]["text"]})))
		# 강태오 소식
		var rv := pro.league.rival_player()
		if rv != null and rv.season.pa > 0 and pro.league.weeks_done % 2 == 0:
			made.append(_news(cfg, rng, "rival", base.merged({"season": rv.line_text()})))
		# 순위 (4주마다)
		if pro.league.weeks_done % 4 == 0 and pro.league.weeks_done > 0:
			var rows := pro.league.standings()
			made.append(_news(cfg, rng, "standings", base.merged({"first": (rows[0] as ProTeam).name, "rank": str(pro.league.rank_of(pro.team_id))})))
	# 팀 성적 반응
	var r := 0
	for g: Dictionary in pro.week_games:
		r += int(g["result"])
	if not pro.week_games.is_empty():
		made.append(_sns(cfg, rng, pro, "team_win" if r > 0 else "team_loss" if r < 0 else "team_even", base))
	_push(career, pro, made, int(cfg.pro["feedPerWeek"]))


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
		"title": fill(pool[rng.randi_range(0, pool.size() - 1)], tokens), "body": "", "likes": 0, "comments": []}


func _sns(cfg: CareerConfig, rng: RandomNumberGenerator, pro: ProCareer, type: String, tokens: Dictionary) -> Dictionary:
	var sc: Dictionary = cfg.pro_content["sns"]
	var pool: Array = sc["posts"].get(type, [])
	if pool.is_empty():
		return {}
	var handles: Array = sc["handles"]
	var comments: Array = sc["comments"].get(type, sc["comments"]["_"])
	var picked: Array = []
	for i in mini(int(cfg.pro["snsComments"]), comments.size()):
		var c: String = fill(comments[rng.randi_range(0, comments.size() - 1)], tokens)
		if not c in picked:
			picked.append(c)
	# 이야기 인물이 올리는 글은 그 사람 아이디 (pro.sns.postHandles)
	var source: String = sc.get("postHandles", {}).get(type, handles[rng.randi_range(0, handles.size() - 1)])
	var likes := int(cfg.pro["snsLikesBase"]) + pro.fame * int(cfg.pro["snsLikesPerFame"]) + rng.randi_range(0, int(cfg.pro["snsLikesBase"]))
	return {"kind": "sns", "type": type, "source": source, "title": fill(pool[rng.randi_range(0, pool.size() - 1)], tokens),
		"body": "", "likes": likes, "comments": picked}


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
		"leaderTeam", "text", "first", "round", "winner", "loser", "wins", "award", "salary", "title", "id", "kind", "handle"]
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
	for t: String in texts:
		for tok in EventBook._tokens(t):
			if not tok in known:
				errors.append("뉴스·SNS 틀 '%s': 모르는 치환 {%s}" % [t, tok])
	return errors
