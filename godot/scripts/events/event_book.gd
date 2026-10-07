class_name EventBook
extends RefCounted
## 돌발 이벤트·스토리 (content.json events, 형식은 eventsNote). 로직만 — 화면은 DialogueView 가 그린다.
##
## 한 주에 나오는 이벤트 (career.story.maxEventsPerWeek 개까지, 주 시작 + 경기 뒤 합):
##   1. 때가 된 지연 결과(schedule 로 예약된 이벤트)와 조건이 맞는 스토리 이벤트(story) — 반드시 나온다
##   2. 그게 없으면 경기 뒤에 eventChance 확률로 무작위 이벤트 하나 (weight 로 뽑는다)
## 시즌 결산(seasonEnd) 이벤트는 주당 개수에 넣지 않는다.
## 이벤트는 기본적으로 커리어에 한 번. repeat 면 cooldownWeeks 주 뒤에 다시 나올 수 있다.
## 선택 결과가 확률로 갈리는 선택지(outcomes)는 커리어 시드로 뽑는다 (원칙 2).
## 선택지에 cost(원)가 있으면 돈이 모자랄 때 고를 수 없다 (choice_block).
## 대화 화면용: lines(대사 줄 나누기) · place_of(배경 자리) · portrait_of(일러스트 자리).

const TIMINGS := ["start", "after", "seasonEnd"]
const CONDITION_KEYS := ["id", "speaker", "title", "text", "choices", "place", "portrait", "partner", "minMoney",
	"story", "timing", "grade", "week", "repeat", "cooldownWeeks", "weight",
	"requires", "forbids", "met", "forbidsMet", "minScout", "maxScout", "minRel", "maxRel", "minRep", "maxRep",
	"maxForm", "maxCondition", "injured", "opponent", "scheduledOnly",
	"lastResult", "lastMinHits", "lastMaxHits", "lastMinHomeRuns", "lastPhaseEnd", "lastNational", "lastOpponent", "seasonOver"]
const EFFECT_KEYS := ["condition", "scoutInterest", "lineupSlot", "contact", "power", "eye", "speed", "form",
	"reputation", "injury", "rel", "meet", "flag", "clearFlag", "schedule", "path", "money", "partner"]
const PATHS := ["draft", "college"]
const PHASE_ENDS := ["champion", "eliminated", "leagueEnd"]

## id -> 정의, content 순서
var defs := {}
var order: Array[String] = []
## id -> 마지막으로 고른 주 (CareerState.total_week)
var fired := {}
## 예약된 지연 결과 [{"id", "due"}] (due 주부터 나온다)
var scheduled: Array = []
## 지난 이야기 [{"season", "grade", "week", "title", "speaker", "choice", "result", "story"}]
var log: Array = []
## 마지막 선택의 변화 설명 ("컨디션 +5" …) — 대화 화면이 결과 아래에 따로 보여 준다
var last_parts: Array[String] = []
## 대기 중인 이벤트: timing -> [정의], 그걸 뽑은 주
var _pending := {}
var _pending_week := {}
## 주별로 뽑힌 이벤트 수 (total_week -> 수)
var _count := {}


func _init(cfg: CareerConfig) -> void:
	for e: Dictionary in cfg.events:
		defs[e["id"]] = e
		order.append(e["id"])


static func timing_of(e: Dictionary) -> String:
	return e.get("timing", "after")


## 이번 주 이 시점의 이벤트들 (아직 고르지 않은 것). 같은 주에 다시 불러도 같은 목록이다
func pending(career: CareerState, timing: String) -> Array:
	var key := career.total_week
	if _pending_week.get(timing, -1) != key:
		_pending_week[timing] = key
		_pending[timing] = _pick(career, timing)
	return _pending[timing]


func _pick(career: CareerState, timing: String) -> Array:
	var tw := career.total_week
	var slots := 99 if timing == "seasonEnd" else int(career.cfg.story["maxEventsPerWeek"]) - int(_count.get(tw, 0))
	if slots <= 0:
		return []
	var out := []
	for item: Dictionary in scheduled:
		var e: Dictionary = defs[item["id"]]
		if int(item["due"]) <= tw and timing_of(e) == timing and eligible(career, e, true) and not out.has(e):
			out.append(e)
	for id in order:
		var e: Dictionary = defs[id]
		if e.get("story", false) and timing_of(e) == timing and eligible(career, e) and not out.has(e):
			out.append(e)
	out = out.slice(0, slots)
	if out.is_empty() and timing == "after":
		var rng := career._rng("event")
		if rng.randf() < career.cfg.event_chance:
			var pool := []
			var total := 0.0
			for id in order:
				var e: Dictionary = defs[id]
				if not e.get("story", false) and timing_of(e) == "after" and eligible(career, e):
					pool.append(e)
					total += float(e.get("weight", 1))
			var roll := rng.randf() * total
			for e: Dictionary in pool:
				roll -= float(e.get("weight", 1))
				if roll < 0.0:
					out.append(e)
					break
	if timing != "seasonEnd":
		_count[tw] = int(_count.get(tw, 0)) + out.size()
	return out


## 조건 검사. via_schedule: 예약으로 나오는 중인가 (scheduledOnly 이벤트는 예약으로만)
func eligible(career: CareerState, e: Dictionary, via_schedule: bool = false) -> bool:
	var p := career.player
	var tw := career.total_week
	if e.get("scheduledOnly", false) and not via_schedule:
		return false
	if fired.has(e["id"]):
		if not e.get("repeat", false):
			return false
		if tw - int(fired[e["id"]]) < int(e.get("cooldownWeeks", 0)):
			return false
	# 다른 시점 대기 목록에 이미 올라 있으면 (아직 안 고른) 또 뽑지 않는다
	for t: String in _pending:
		if _pending_week.get(t, -1) == tw and (_pending[t] as Array).has(e) and t != timing_of(e):
			return false
	if not _in_span(e, "grade", p.grade) or not _in_span(e, "week", career.week):
		return false
	if e.has("partner"):
		var want: String = e["partner"]
		var now := career.people.partner
		if (want == "none" and now != "") or (want == "any" and now == "") or (want != "none" and want != "any" and want != now):
			return false
	if e["speaker"] == "partner" and career.people.partner == "":
		return false
	if p.money < int(e.get("minMoney", 0)):
		return false
	for f: String in e.get("requires", []):
		if not p.flags.has(f):
			return false
	for f: String in e.get("forbids", []):
		if p.flags.has(f):
			return false
	for id: String in e.get("met", []):
		if not career.people.is_met(id):
			return false
	for id: String in e.get("forbidsMet", []):
		if career.people.is_met(id):
			return false
	if p.scout_interest < int(e.get("minScout", 0)) or p.scout_interest > int(e.get("maxScout", 100)):
		return false
	if p.reputation < int(e.get("minRep", 0)) or p.reputation > int(e.get("maxRep", 100)):
		return false
	var min_rel: Dictionary = e.get("minRel", {})
	for id: String in min_rel:
		if career.people.relation(id) < int(min_rel[id]):
			return false
	var max_rel: Dictionary = e.get("maxRel", {})
	for id: String in max_rel:
		if career.people.relation(id) > int(max_rel[id]):
			return false
	if e.has("maxForm") and p.form > float(e["maxForm"]):
		return false
	if e.has("maxCondition") and p.condition > int(e["maxCondition"]):
		return false
	if e.has("injured") and p.is_injured() != bool(e["injured"]):
		return false
	if e.has("opponent"):
		if career.is_season_over() or career.opponent().id != career.rival.school_id:
			return false
	return _last_ok(career, e)


## 지난 경기 조건 (경기 뒤 이벤트용)
func _last_ok(career: CareerState, e: Dictionary) -> bool:
	var last := career.last
	var needs_last := false
	for k: String in e:
		if k.begins_with("last") or k == "seasonOver":
			needs_last = true
	if not needs_last:
		return true
	if last.is_empty():
		return false
	if e.has("lastResult") and last["result"] != e["lastResult"]:
		return false
	if last["hits"] < int(e.get("lastMinHits", 0)) or last["hits"] > int(e.get("lastMaxHits", 99)):
		return false
	if last["home_runs"] < int(e.get("lastMinHomeRuns", 0)):
		return false
	if e.has("lastPhaseEnd") and last["phase_end"] != e["lastPhaseEnd"]:
		return false
	if e.has("lastNational") and last["national"] != bool(e["lastNational"]):
		return false
	if e.has("lastOpponent") and last["opponent_id"] != career.rival.school_id:
		return false
	if e.has("seasonOver") and last["season_over"] != bool(e["seasonOver"]):
		return false
	return true


static func _in_span(e: Dictionary, key: String, v: int) -> bool:
	if not e.has(key):
		return true
	var span: Array = e[key]
	return v >= int(span[0]) and v <= int(span[1])


## 선택. 돌려주는 값: 결과 문장 (+ 변화 설명)
func resolve(career: CareerState, e: Dictionary, choice_index: int) -> String:
	var id: String = e["id"]
	fired[id] = career.total_week
	for t: String in _pending:
		(_pending[t] as Array).erase(e)
	scheduled = scheduled.filter(func(item: Dictionary) -> bool: return item["id"] != id)
	var choice: Dictionary = e["choices"][choice_index]
	var cost := int(choice.get("cost", 0))
	assert(career.player.money >= cost, "돈이 모자란 선택지를 골랐다")
	career.player.money -= cost
	var result: String
	var fx: Dictionary
	if choice.has("outcomes"):
		var o := _roll_outcome(choice["outcomes"], career._rng("choice:" + id))
		result = o["result"]
		fx = o.get("effects", {})
	else:
		result = choice["result"]
		fx = choice.get("effects", {})
	var speaker := speaker_name(career, e)
	var parts := apply_effects(career, fx)
	if cost > 0:
		parts.push_front("돈 -%s" % PlayerData.money_text(cost))
	last_parts = parts
	var text := fill(career, result)
	log.append({"season": career.season_no, "grade": career.player.grade, "week": career.week, "title": e.get("title", id),
		"speaker": speaker, "choice": fill(career, choice["label"]), "result": text, "story": e.get("story", false)})
	return text if parts.is_empty() else "%s (%s)" % [text, ", ".join(parts)]


static func _roll_outcome(outcomes: Array, rng: RandomNumberGenerator) -> Dictionary:
	var total := 0.0
	for o: Dictionary in outcomes:
		total += float(o["weight"])
	var roll := rng.randf() * total
	for o: Dictionary in outcomes:
		roll -= float(o["weight"])
		if roll < 0.0:
			return o
	return outcomes[-1]


## 효과 적용. 돌려주는 값: 화면에 보일 변화 설명들
func apply_effects(career: CareerState, fx: Dictionary) -> Array[String]:
	var p := career.player
	var cfg := career.cfg
	var parts: Array[String] = []
	# partner(연애 시작·이별)는 맨 나중에 — 같은 선택의 rel{partner} 가 지금 상대에게 먼저 적용되게
	var keys: Array = fx.keys()
	if keys.has("partner"):
		keys.erase("partner")
		keys.append("partner")
	for key: String in keys:
		var v: Variant = fx[key]
		match key:
			"condition":
				p.add_condition(int(v))
				parts.append("컨디션 %+d" % int(v))
			"scoutInterest":
				p.add_scout(int(v))
				parts.append("스카우트 관심 %+d" % int(v))
			"reputation":
				p.add_reputation(int(v))
				parts.append("평판 %+d" % int(v))
			"lineupSlot":
				p.lineup_slot = int(v)
				parts.append("타순 %d번" % int(v))
			"form":
				var m := float(cfg.form["max"])
				p.form = clampf(p.form + float(v), -m, m)
				parts.append("폼 ▲" if float(v) > 0 else "폼 ▼")
			"injury":
				p.injury_weeks = maxi(p.injury_weeks, int(v))
				parts.append("부상 %d주" % int(v))
			"rel":
				for id: String in v:
					var person := career.people.person(id)
					if person == null:
						continue
					career.people.add_relation(id, int(v[id]))
					parts.append("%s %+d" % [rel_name(person), int(v[id])])
			"meet":
				for id: String in _list(v):
					career.people.meet(id)
			"flag":
				for f: String in _list(v):
					p.flags[f] = true
			"clearFlag":
				for f: String in _list(v):
					p.flags.erase(f)
			"schedule":
				scheduled.append({"id": v["event"], "due": career.total_week + int(v["weeks"])})
			"path":
				career.path = str(v)
			"money":
				p.money = maxi(0, p.money + int(v))
				parts.append("돈 %s%s" % ["+" if int(v) > 0 else "", PlayerData.money_text(int(v))])
			"partner":
				career.people.partner = "" if str(v) == "none" else str(v)
				parts.append("이별" if str(v) == "none" else "연애 시작 ♥")
			_:
				if key in PlayerData.STATS:
					p.add_stat(key, int(v), cfg)
					parts.append("%s %+d" % [PlayerData.STAT_LABELS[key], int(v)])
				else:
					assert(false, "content.json: 알 수 없는 이벤트 효과 %s" % key)
	return parts


static func _list(v: Variant) -> Array:
	return v if v is Array else [v]


## "감독 신뢰", "라이벌 의식", "동기 우정", "소율 호감"
static func rel_name(person: People.Person) -> String:
	if person.romance:
		return "%s %s" % [person.name.substr(1), person.rel_label]
	return person.rel_label if person.rel_label.contains(person.role) else "%s %s" % [person.role, person.rel_label]


## 선택지를 못 고르는 이유 ("" 고를 수 있음)
static func choice_block(career: CareerState, choice: Dictionary) -> String:
	var cost := int(choice.get("cost", 0))
	if career.player.money < cost:
		return "돈이 모자라다 (%s 필요)" % PlayerData.money_text(cost)
	return ""


## 대화 화면 대사 줄: [{"who": 인물 id | "" (지문) | "?" (인물이 아닌 화자), "name", "role", "text"}]
## 본문 맨 앞 괄호는 지문으로 따로 뺀다: "(몸을 풀던 …) 네가 새싹고 1학년?" → 지문 + 화자 대사
static func lines(career: CareerState, e: Dictionary) -> Array:
	var t := fill(career, e["text"])
	var out := []
	if t.begins_with("("):
		var close := t.find(")")
		if close > 0:
			out.append({"who": "", "name": "", "role": "", "text": t.substr(1, close - 1).strip_edges()})
			t = t.substr(close + 1).strip_edges()
	if t != "":
		var s: String = e["speaker"]
		var who := s if career.people.has(s) else "?"
		if s == "partner":
			who = career.people.partner
		out.append({"who": who, "name": speaker_name(career, e), "role": speaker_role(career, e), "text": t})
	return out


## 배경 자리 id: 이벤트 place → 화자의 기본 장소 → field
static func place_of(career: CareerState, e: Dictionary) -> String:
	if e.has("place"):
		return e["place"]
	var person := career.people.person(e["speaker"])
	if person != null and person.place != "":
		return person.place
	return "field"


## 일러스트 자리 id ("none" 이면 없음): 이벤트 portrait → 인물 id → none
static func portrait_of(career: CareerState, e: Dictionary) -> String:
	if e.has("portrait"):
		return e["portrait"]
	var person := career.people.person(e["speaker"])
	return person.id if person != null else "none"


## 말하는 사람 이름 (인물 id 면 부르는 이름, 아니면 그대로)
static func speaker_name(career: CareerState, e: Dictionary) -> String:
	var s: String = e["speaker"]
	return career.people.person(s).call if career.people.has(s) else s


## 말하는 사람 역할 (인물이 아니면 "")
static func speaker_role(career: CareerState, e: Dictionary) -> String:
	var s: String = e["speaker"]
	return career.people.person(s).role if career.people.has(s) else ""


## 글자 치환 ({player}, {rival}, {pick} …)
static func fill(career: CareerState, text: String) -> String:
	if not text.contains("{"):
		return text
	var p := career.player
	var t := text.replace("{player}", p.name).replace("{school}", career.my_school.name)
	var partner := career.people.person("partner")
	t = t.replace("{partnerName}", partner.name if partner != null else "").replace("{partner}", partner.call if partner != null else "")
	for id: String in career.people.by_id:
		var person: People.Person = career.people.by_id[id]
		t = t.replace("{%sName}" % id, person.name).replace("{%s}" % id, person.call)
	t = t.replace("{rivalSchool}", career.schools[career.rival.school_id].name)
	t = t.replace("{scoutTeam}", career.people.person("scout").team_name)
	var last := career.rival.last_line
	t = t.replace("{rivalLast}", last.line_text() if last != null else "아직 경기 전")
	if t.contains("{pick}"):
		t = t.replace("{pick}", career.projection()["text"])
	t = t.replace("{rank}", "유망주 랭킹 %d위" % career.prospect_rank())
	return josa(t)


## "이민재이(가)" → "이민재가": 앞 글자 받침으로 조사를 고른다 (한글이 아니면 그대로)
static func josa(t: String) -> String:
	for pair: Array in [["이(가)", "이", "가"], ["을(를)", "을", "를"], ["은(는)", "은", "는"], ["과(와)", "과", "와"]]:
		var mark: String = pair[0]
		var i := t.find(mark)
		while i > 0:
			var code := t.unicode_at(i - 1)
			if code >= 0xAC00 and code <= 0xD7A3:
				var word: String = pair[1] if (code - 0xAC00) % 28 != 0 else pair[2]
				t = t.substr(0, i) + word + t.substr(i + mark.length())
			i = t.find(mark, i + 1)
	return t


## 지금 챕터 이름 ("1학년 · 신입생")
static func chapter(career: CareerState) -> String:
	return career.cfg.chapters.get(str(career.player.grade), "")


# ---------- 무결성 검사 (테스트용) ----------

## 이벤트 정의 검사. 돌려주는 값: 문제 설명 목록 (비었으면 통과)
static func validate(cfg: CareerConfig) -> Array[String]:
	var errors: Array[String] = []
	var ids := {}
	var flags_set := {}
	var schedule_targets := {}
	for e: Dictionary in cfg.events:
		for c: Dictionary in e.get("choices", []):
			for fx: Dictionary in _choice_effects(c):
				for f in _list(fx.get("flag", [])):
					flags_set[f] = true
				if fx.has("schedule"):
					schedule_targets[fx["schedule"].get("event", "")] = true
	var known_tokens := ["player", "school", "rivalSchool", "scoutTeam", "rivalLast", "pick", "rank", "partner", "partnerName"]
	var people_ids: Array = cfg.people.keys()
	people_ids.append("partner")
	for id: String in cfg.people:
		known_tokens.append(id)
		known_tokens.append(id + "Name")
	for e: Dictionary in cfg.events:
		var id: String = e.get("id", "")
		var where := "이벤트 %s" % id
		if id == "" or ids.has(id):
			errors.append("%s: id 가 없거나 겹친다" % where)
		ids[id] = true
		for k: String in e:
			if not k in CONDITION_KEYS:
				errors.append("%s: 모르는 키 %s" % [where, k])
		if not timing_of(e) in TIMINGS:
			errors.append("%s: timing %s" % [where, timing_of(e)])
		if str(e.get("speaker", "")) == "" or str(e.get("text", "")) == "":
			errors.append("%s: speaker·text 가 비었다" % where)
		for k in ["grade", "week"]:
			if e.has(k) and ((e[k] as Array).size() != 2 or int(e[k][0]) > int(e[k][1])):
				errors.append("%s: %s 범위" % [where, k])
		for f: String in e.get("requires", []):
			if not flags_set.has(f):
				errors.append("%s: requires %s 를 세우는 선택지가 없다" % [where, f])
		for k in ["met", "forbidsMet"]:
			for pid: String in e.get(k, []):
				if not cfg.people.has(pid):
					errors.append("%s: %s 인물 %s 없음" % [where, k, pid])
		for k in ["minRel", "maxRel"]:
			for pid: String in e.get(k, {}):
				if not pid in people_ids:
					errors.append("%s: %s 인물 %s 없음" % [where, k, pid])
		if e.has("partner") and not (e["partner"] in ["none", "any"] or _is_romance(cfg, e["partner"])):
			errors.append("%s: partner 조건 %s" % [where, e["partner"]])
		if (e.get("speaker", "") == "partner" or (e.get("minRel", {}) as Dictionary).has("partner") or (e.get("maxRel", {}) as Dictionary).has("partner")) and e.get("partner", "") != "any":
			errors.append("%s: partner 를 쓰면 partner: any 조건이 있어야 한다" % where)
		if e.has("place") and not cfg.places.has(e["place"]):
			errors.append("%s: place %s 없음" % [where, e["place"]])
		if e.has("opponent") and e["opponent"] != "rival":
			errors.append("%s: opponent 는 rival 만" % where)
		if e.has("lastOpponent") and e["lastOpponent"] != "rival":
			errors.append("%s: lastOpponent 는 rival 만" % where)
		if e.has("lastPhaseEnd") and not e["lastPhaseEnd"] in PHASE_ENDS:
			errors.append("%s: lastPhaseEnd %s" % [where, e["lastPhaseEnd"]])
		if e.has("lastResult") and not e["lastResult"] in ["win", "loss", "draw"]:
			errors.append("%s: lastResult %s" % [where, e["lastResult"]])
		if e.get("scheduledOnly", false) and not schedule_targets.has(id):
			errors.append("%s: scheduledOnly 인데 예약하는 선택지가 없다" % where)
		if not cfg.people.has(e.get("speaker", "")) and str(e.get("speaker", "")).begins_with("{"):
			errors.append("%s: speaker" % where)
		var texts: Array = [e.get("text", "")]
		var choices: Array = e.get("choices", [])
		if choices.is_empty():
			errors.append("%s: 선택지가 없다" % where)
		for c: Dictionary in choices:
			texts.append(c.get("label", ""))
			if str(c.get("label", "")) == "":
				errors.append("%s: 선택지 label 이 비었다" % where)
			if c.has("cost") and int(c["cost"]) <= 0:
				errors.append("%s: cost 는 양수" % where)
			if c.has("outcomes"):
				if (c["outcomes"] as Array).is_empty():
					errors.append("%s: outcomes 가 비었다" % where)
				for o: Dictionary in c["outcomes"]:
					if float(o.get("weight", 0)) <= 0.0 or str(o.get("result", "")) == "":
						errors.append("%s: outcome 의 weight·result" % where)
					texts.append(o.get("result", ""))
			elif str(c.get("result", "")) == "":
				errors.append("%s: 선택지 result 가 비었다" % where)
			else:
				texts.append(c["result"])
			for fx: Dictionary in _choice_effects(c):
				for k: String in fx:
					if not k in EFFECT_KEYS:
						errors.append("%s: 모르는 효과 %s" % [where, k])
				for pid: String in fx.get("rel", {}):
					if not pid in people_ids:
						errors.append("%s: rel 인물 %s 없음" % [where, pid])
					if pid == "partner" and e.get("partner", "") != "any":
						errors.append("%s: rel partner 는 partner: any 이벤트에서만" % where)
				if fx.has("partner") and not (fx["partner"] == "none" or _is_romance(cfg, fx["partner"])):
					errors.append("%s: partner 효과 %s" % [where, fx["partner"]])
				for pid in _list(fx.get("meet", [])):
					if not cfg.people.has(pid):
						errors.append("%s: meet 인물 %s 없음" % [where, pid])
				if fx.has("schedule"):
					var target: String = fx["schedule"].get("event", "")
					if not _has_event(cfg, target) or int(fx["schedule"].get("weeks", 0)) <= 0:
						errors.append("%s: schedule 대상 %s" % [where, target])
				if fx.has("path") and not fx["path"] in PATHS:
					errors.append("%s: path %s" % [where, fx["path"]])
		for t: String in texts:
			for tok in _tokens(t):
				if not tok in known_tokens:
					errors.append("%s: 모르는 글자 치환 {%s}" % [where, tok])
	return errors


static func _choice_effects(c: Dictionary) -> Array:
	if c.has("outcomes"):
		var out := []
		for o: Dictionary in c["outcomes"]:
			out.append(o.get("effects", {}))
		return out
	return [c.get("effects", {})]


static func _is_romance(cfg: CareerConfig, id: String) -> bool:
	return cfg.people.has(id) and cfg.people[id].get("romance", false)


static func _has_event(cfg: CareerConfig, id: String) -> bool:
	for e: Dictionary in cfg.events:
		if e["id"] == id:
			return true
	return false


static func _tokens(t: String) -> Array:
	var out := []
	var i := t.find("{")
	while i >= 0:
		var j := t.find("}", i)
		if j < 0:
			break
		out.append(t.substr(i + 1, j - i - 1))
		i = t.find("{", j)
	return out
