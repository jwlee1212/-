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
## 선택지에 cost(원)가 있으면 돈이 모자랄 때 고를 수 없다.
## 이벤트는 대본(script_of)으로 진행한다: 여러 인물의 대사 줄과 선택 지점이 이어지고, 선택마다 뒤따르는 대사가 붙는다.
## 한 걸음씩 읽는 건 DialogueRun, 끝나면 commit 이 효과를 한꺼번에 반영한다. 화면용: place_of(배경 자리) · portrait_of(일러스트 자리).

const TIMINGS := ["start", "after", "seasonEnd", "game"]
## 대본 줄의 표정 (face, 없으면 기본). 그림은 portraits/<인물 id>_<표정>.png — 그 그림이 없으면 기본 그림을 쓴다
const FACES := ["happy", "pout", "surprised"]
## 경기 중 대화가 뜨는 순간 (timing game 이벤트의 moment): 내 첫 타석 직전 · 대타로 나가는 타석 직전 · 승부처 타석 직전 · 내 홈런 직후 · 내 삼진 직후
## · scene(경기 대본 gamePlan 의 장면 직전 — 장면의 event 로만 뜬다)
const MOMENTS := ["first_pa", "pinch_pa", "clutch_pa", "after_homer", "after_strikeout", "scene"]
const CONDITION_KEYS := ["id", "speaker", "title", "text", "choices", "script", "place", "portrait", "partner", "minMoney", "winter", "campWeek", "moment", "free",
	"story", "timing", "grade", "week", "repeat", "cooldownWeeks", "weight",
	"requires", "forbids", "met", "forbidsMet", "minScout", "maxScout", "minRel", "maxRel", "minRep", "maxRep",
	"maxForm", "maxCondition", "injured", "opponent", "scheduledOnly",
	"recentGames", "recentMinHits", "recentMaxHits", "stage",
	"lastResult", "lastMinHits", "lastMaxHits", "lastMinHomeRuns", "lastPhaseEnd", "lastNational", "lastOpponent", "seasonOver",
	"after", "when", "episode"]
const EFFECT_KEYS := ["condition", "scoutInterest", "lineupSlot", "contact", "power", "eye", "speed", "fielding", "form",
	"reputation", "injury", "rel", "meet", "flag", "clearFlag", "schedule", "path", "money", "partner", "campFocus", "gear", "gameBoost", "pinchHit",
	"matchOverride", "injuryLabel", "minigame", "set", "add", "train", "gamePlan", "gradeVar", "addContact"]
## 대본 줄의 화면 표시 (ui, 없으면 보통 대사·지문): 폰 문자 · 노트북 화면 · TV · 화이트보드 · 전광판 · 쪽지 · 시스템 알림
const UI_KINDS := ["phone", "laptop", "tv", "whiteboard", "scoreboard", "item", "system", "news"]
## 조건(cond)의 비교 키: {"var": id, <키>: 값}
const COMPARES := ["eq", "ne", "gte", "lte", "gt", "lt", "in"]
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
## 같은 주에 다시 뽑아야 하는 시점 (timing -> total_week). 주 시작 스토리가 끝나면 그다음 이야기를 이어서 찾는다
var _repick := {}


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
		# 같은 주에 다시 뽑는 경우(이어지는 주 시작 스토리)엔 이미 올라 있던 이벤트를 개수에 또 세지 않는다
		var again: bool = _repick.get(timing, -1) == key
		var before: Array = _pending.get(timing, []) if again else []
		_repick.erase(timing)
		_pending_week[timing] = key
		_pending[timing] = _pick(career, timing, before, again)
	return _pending[timing]


## 경기 중 이벤트 하나 (없으면 빈 Dictionary): 이 순간(moment)에 맞는 timing game 이벤트 중에서.
## 스토리는 조건이 맞으면 반드시, 무작위는 gameEventChance 확률로 (weight 로 뽑는다). 한 주 이벤트 개수에 같이 센다.
## salt: 같은 경기 안에서 순간을 구분하는 글자 (몇 번째 타석인지)
func pick_game(career: CareerState, moment: String, salt: String) -> Dictionary:
	var tw := career.total_week
	# 한 주 개수가 찼으면 free 이벤트만 (개수 제한 밖)
	var full := int(_count.get(tw, 0)) >= int(career.cfg.story["maxEventsPerWeek"])
	var story := []
	var pool := []
	var total := 0.0
	for id in order:
		var e: Dictionary = defs[id]
		if timing_of(e) != "game" or e.get("moment", "") != moment or (full and not e.get("free", false)) or not eligible(career, e):
			continue
		if e.get("story", false):
			story.append(e)
		else:
			pool.append(e)
			total += float(e.get("weight", 1))
	var picked := {}
	if not story.is_empty():
		# 한 번뿐인 이야기가 되풀이되는 이야기(repeat — 평소의 대타 호출 같은)보다 먼저
		picked = story[0]
		for e: Dictionary in story:
			if not e.get("repeat", false):
				picked = e
				break
	elif not pool.is_empty():
		var rng := career._rng("game-event:%s:%s" % [moment, salt])
		if rng.randf() < float(career.cfg.story["gameEventChance"]):
			var roll := rng.randf() * total
			for e: Dictionary in pool:
				roll -= float(e.get("weight", 1))
				if roll < 0.0:
					picked = e
					break
	if not picked.is_empty() and not picked.get("free", false):
		_count[tw] = int(_count.get(tw, 0)) + 1
	return picked


## 이 순간(moment)에 조건이 맞는 한 번뿐인(repeat 아님) 경기 중 스토리가 있나.
## pinch_pa 면 벤치(타순 0)에 있는 내 선수를 이번 경기에 대타로 부를 이야기가 있다는 뜻이다
func has_game_story(career: CareerState, moment: String) -> bool:
	for id in order:
		var e: Dictionary = defs[id]
		if timing_of(e) == "game" and e.get("moment", "") == moment and e.get("story", false) and not e.get("repeat", false) and eligible(career, e):
			return true
	return false


## 이번 경기에 장면을 연출할 이야기 (없으면 빈 Dictionary): 조건이 맞는 한 번뿐인 경기 중 스토리 가운데 stage 가 있는 첫 번째.
## role = CareerState.next_role() — 대타 장면(pinch_pa)은 대타로 나가는 경기에만, 나머지는 선발 경기에만
func staged_story(career: CareerState, role: String) -> Dictionary:
	if role != "start" and role != "pinch":
		return {}
	for id in order:
		var e: Dictionary = defs[id]
		if not e.has("stage") or timing_of(e) != "game" or e.get("repeat", false) or (e["moment"] == "pinch_pa") != (role == "pinch"):
			continue
		if eligible(career, e):
			return e
	return {}


## 이번 주 주 시작·경기 뒤 이야기를 다시 뽑게 한다 (캠프가 시작되면 같은 주라도 나올 이야기가 달라진다)
func reset_week() -> void:
	_pending_week.erase("start")
	_pending_week.erase("after")


## counted: 이미 이번 주 개수에 들어가 있는 이벤트들 (다시 뽑을 때). again: 같은 주에 다시 뽑는 중인가 (무작위 이벤트는 처음 한 번만 굴린다)
func _pick(career: CareerState, timing: String, counted: Array = [], again: bool = false) -> Array:
	var tw := career.total_week
	var slots := 99 if timing == "seasonEnd" else int(career.cfg.story["maxEventsPerWeek"]) - int(_count.get(tw, 0)) + counted.size()
	var out := []
	for item: Dictionary in scheduled:
		if not defs.has(item["id"]):
			continue  # 아직 없는 이벤트 (content.json pendingEvents)
		var e: Dictionary = defs[item["id"]]
		if int(item["due"]) <= tw and timing_of(e) == timing and eligible(career, e, true) and not out.has(e):
			out.append(e)
	for id in order:
		var e: Dictionary = defs[id]
		if e.get("story", false) and timing_of(e) == timing and eligible(career, e) and not out.has(e):
			out.append(e)
	# free 이벤트는 개수 제한 밖 (연달아 이어지는 이야기). 나머지는 남은 자리만큼
	var kept := []
	for e: Dictionary in out:
		if e.get("free", false):
			kept.append(e)
		elif slots > 0:
			kept.append(e)
			slots -= 1
	out = kept
	if out.is_empty() and timing == "after" and slots > 0 and not again:
		var rng := career._rng("event")
		if rng.randf() < (float(career.cfg.camp["eventChance"]) if career.in_camp() else career.cfg.event_chance):
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
		var added := 0
		for e: Dictionary in out:
			if not e.get("free", false) and not counted.has(e):
				added += 1
		_count[tw] = int(_count.get(tw, 0)) + added
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
	# 겨울 캠프: winter true 는 캠프에서만, any 는 둘 다, 없으면 시즌에서만 (예약된 지연 결과는 어디서든)
	var winter := str(e.get("winter", "no"))
	if not via_schedule and winter != "any" and (winter == "true") != career.in_camp():
		return false
	if not _in_span(e, "campWeek", career.camp_week):
		return false
	if not _in_span(e, "grade", p.grade) or (not career.in_camp() and not _in_span(e, "week", career.week)):
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
	# after: 이 이벤트들을 다 본 뒤에만 (대본의 장면 순서 — 플래그를 따로 세우지 않아도 된다)
	for id: String in e.get("after", []):
		if not fired.has(id):
			return false
	if e.has("when") and not test(career, e["when"]):
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
	if e.has("recentGames"):
		# 내가 나온 최근 N경기 안타 합 (공식 경기만, 아직 한 경기도 안 나왔으면 조건이 안 맞는다)
		var recent := career.records.recent_hits(int(e["recentGames"]))
		if recent < 0 or recent < int(e.get("recentMinHits", 0)) or recent > int(e.get("recentMaxHits", 999)):
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


## 화면 없이 끝까지 고른다: 선택 지점마다 choice_index 번째(막혀 있으면 고를 수 있는 첫 번째). 돌려주는 값: 결과 문장 (+ 변화 설명)
func resolve(career: CareerState, e: Dictionary, choice_index: int) -> String:
	var run := DialogueRun.new(career, e)
	run.auto(choice_index)
	return run.commit()


## 대화가 끝났다: 고른 선택들의 돈·효과를 한꺼번에 반영하고 기록에 남긴다. 돌려주는 값: 결과 문장 (+ 변화 설명)
func commit(run: DialogueRun) -> String:
	var career := run.career
	var e := run.event
	var id: String = e["id"]
	fired[id] = career.total_week
	for t: String in _pending:
		(_pending[t] as Array).erase(e)
	scheduled = scheduled.filter(func(item: Dictionary) -> bool: return item["id"] != id)
	# 스토리가 끝났다: 이 이야기가 세운 플래그로 열리는 같은 시점(주 시작·경기 뒤·시즌 결산)의 다음 이야기를 같은 주에 이어서 띄운다
	var t_of := timing_of(e)
	if e.get("story", false) and t_of != "game" and _pending_week.get(t_of, -1) == career.total_week:
		_repick[t_of] = career.total_week
		_pending_week.erase(t_of)
	assert(career.player.money >= run.cost, "돈이 모자란 선택지를 골랐다")
	career.player.money -= run.cost
	var speaker := speaker_name(career, e)
	var parts: Array[String] = []
	for fx: Dictionary in run.effects:
		parts.append_array(apply_effects(career, fx))
	if run.cost > 0:
		parts.push_front("돈 -%s" % PlayerData.money_text(run.cost))
	last_parts = parts
	# 이야기 속 문자는 폰 대화방 기록에도 남는다
	for pl: Dictionary in run.phone_log:
		career.phone.add_story_line(pl["room"], pl["from"], pl["text"], career.total_week, id)
	var text := run.last_text
	log.append({"season": career.season_no, "grade": career.player.grade, "week": career.week, "title": e.get("title", id),
		"speaker": speaker, "choice": " → ".join(run.labels), "result": text, "story": e.get("story", false)})
	return text if parts.is_empty() else "%s (%s)" % [text, ", ".join(parts)]


## 이벤트의 대본 (줄과 선택 지점이 순서대로). script 가 있으면 그대로, 옛 형식(text + choices)이면 대본으로 바꾼다:
##   화자 대사 한 줄 → 선택 지점 하나 → 고른 선택지의 result 가 지문 한 줄
static func script_of(e: Dictionary) -> Array:
	if e.has("script"):
		return e["script"]
	var options := []
	for c: Dictionary in e.get("choices", []):
		var o := {"label": c.get("label", "")}
		if c.has("cost"):
			o["cost"] = c["cost"]
		if c.has("outcomes"):
			var outs := []
			for oc: Dictionary in c["outcomes"]:
				outs.append({"weight": oc.get("weight", 0), "effects": oc.get("effects", {}), "then": [{"text": oc.get("result", "")}]})
			o["outcomes"] = outs
		else:
			o["effects"] = c.get("effects", {})
			o["then"] = [{"text": c.get("result", "")}]
		options.append(o)
	return [{"speaker": e.get("speaker", ""), "text": e.get("text", "")}, {"choices": options}]


## 불러온 이벤트 정의 손질: 대본 이벤트에 choices(첫 선택 지점의 선택지들)를 붙여 둔다 — 도구·테스트가 형식과 상관없이 e.choices 를 본다
static func prepare(events: Array) -> void:
	for e: Dictionary in events:
		if e.has("script") and not e.has("choices"):
			for node: Dictionary in e["script"]:
				if node.has("choices"):
					e["choices"] = node["choices"]
					break


static func roll_outcome(outcomes: Array, rng: RandomNumberGenerator) -> Dictionary:
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
				parts.append("타순 %d번" % int(v) if int(v) > 0 else "선발 제외 (벤치)")
			"form":
				var m := float(cfg.form["max"])
				p.form = clampf(p.form + float(v), -m, m)
				parts.append("폼 ▲" if float(v) > 0 else "폼 ▼")
			"injury":
				p.injury_weeks = maxi(p.injury_weeks, int(v))
				parts.append("부상 %d주" % int(v))
			"injuryLabel":
				p.injury_label = str(v)
			"matchOverride":
				career.match_override = (v as Dictionary).duplicate(true)
			"minigame":
				# 문자열 하나(미니게임 id) — 대화가 끝나면 홈이 띄운다 (CareerState.MINIGAMES)
				career.pending_minigame = str(v)
			"set":
				for id: String in v:
					var x: Variant = v[id]
					# JSON 숫자는 실수로 읽힌다 — 정수 변수는 정수로
					if x is float and cfg.vars.get(id, {}).get("type", "") == "int":
						x = int(x)
					p.vars[id] = (x as Array).duplicate() if x is Array else x
			"add":
				var links: Dictionary = cfg.story.get("varRelation", {})
				for id: String in v:
					p.vars[id] = int(var_of(career, id)) + int(v[id])
					# 호감도 변수는 인물 관계(인물 탭)에도 같이 오른다: varRelation {변수: [인물 id, 배율]}
					if links.has(id) and career.people.has(links[id][0]):
						var delta := roundi(float(v[id]) * float(links[id][1]))
						career.people.add_relation(links[id][0], delta)
						parts.append("%s %+d" % [rel_name(career.people.person(links[id][0])), delta])
			"train":
				# 경험치: 훈련 성장과 같은 규칙(잠재력·학년)으로 v 만큼 성장시킨다 — 칸이 안 차면 "조금 성장"
				for st: String in v:
					var raised := Growth.apply(cfg, p, st, float(v[st]))
					parts.append("%s %s" % [PlayerData.STAT_LABELS[st], "+%d" % raised if raised > 0 else "조금 성장"])
			"gamePlan":
				career.game_plan = (v as Dictionary).duplicate(true)
			"gradeVar":
				# 기록으로 등급 변수를 정한다: of lastPhaseAvg = 가장 최근 대회(단계)의 내 타율,
				# 기준은 balance.json career.story.constants[thresholds] = [[이상, 등급]…] (위에서부터 처음 맞는 것)
				var avg := 0.0
				if not career.records.phases.is_empty():
					var ln: PlayerData.SeasonStats = career.records.phases[-1].line
					avg = float(ln.hits) / ln.ab if ln.ab > 0 else 0.0
				var rows: Array = cfg.story["constants"][v["thresholds"]]
				var got: Variant = rows[-1][1]
				for row: Array in rows:
					if avg >= float(row[0]):
						got = row[1]
						break
				p.vars[v["var"]] = got
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
					if cfg.people.get(id, {}).get("contactOnMeet", false):
						career.phone.add_contact(id)
			"addContact":
				# 폰 연락처에 추가 (해금)
				for id: String in _list(v):
					career.phone.add_contact(id)
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
			"gameBoost":
				for st: String in v:
					p.game_boost[st] = int(p.game_boost.get(st, 0)) + int(v[st])
					parts.append("이번 경기 %s %+d" % [PlayerData.STAT_LABELS[st], int(v[st])])
			"pinchHit":
				p.bench_next = bool(v)
				if p.bench_next:
					parts.append("다음 경기 벤치 대기")
			"gear":
				p.gear[str(v)] = true
				parts.append("장비: %s" % cfg.shop["gear"][str(v)]["label"])
			"campFocus":
				career.camp_focus = str(v)
				parts.append("집중 과제: %s" % career.camp_focus_label())
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
		# 성 없는 두 글자 이름(소율·하린)은 그대로, 세 글자면 성을 뺀다
		return "%s %s" % [person.name.substr(1) if person.name.length() >= 3 else person.name, person.rel_label]
	return person.rel_label if person.rel_label.contains(person.role) else "%s %s" % [person.role, person.rel_label]


## 선택지를 못 고르는 이유 ("" 고를 수 있음)
static func choice_block(career: CareerState, choice: Dictionary) -> String:
	var cost := int(choice.get("cost", 0))
	if career.player.money < cost:
		return "돈이 모자라다 (%s 필요)" % PlayerData.money_text(cost)
	return ""


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
	var t := player_josa(text, p.name).replace("{player}", p.name).replace("{school}", career.my_school.name)
	for id: String in career.cfg.vars:
		if t.contains("{%s}" % id):
			t = t.replace("{%s}" % id, str(var_of(career, id)))
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
	if t.contains("{phaseLine}"):
		# 가장 최근 대회(단계)의 내 기록 한 줄 (화이트보드 개인 기록)
		var ln: PlayerData.SeasonStats = career.records.phases[-1].line if not career.records.phases.is_empty() else PlayerData.SeasonStats.new()
		t = t.replace("{phaseLine}", "타율 %s · 출루율 %s · 실책 %d · 보살 %d" % [ln.average(), ln.obp_text(), ln.errors, ln.assists])
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


## 이름 뒤 조사 (docs/scenario 표기): {player}이가 → 받침 있으면 "이가"·없으면 "가", {player}아 → "아"/"야",
## {player}이 → "이"/"" (뒤에 "(가)" 가 붙은 옛 표기 "{player}이(가)" 는 josa() 가 처리하므로 건드리지 않는다)
static func player_josa(t: String, name: String) -> String:
	if not t.contains("{player}") or name == "":
		return t
	var code := name.unicode_at(name.length() - 1)
	var batchim := code >= 0xAC00 and code <= 0xD7A3 and (code - 0xAC00) % 28 != 0
	t = t.replace("{player}이가", name + ("이가" if batchim else "가"))
	t = t.replace("{player}아", name + ("아" if batchim else "야"))
	var i := t.find("{player}이")
	while i >= 0:
		var after := i + "{player}이".length()
		if after < t.length() and t[after] == "(":
			i = t.find("{player}이", after)
			continue
		t = t.substr(0, i) + name + ("이" if batchim else "") + t.substr(after)
		i = t.find("{player}이", i)
	return t


# ---------- 시나리오 변수 · 조건 ----------

## 선언한 변수의 기본값 (content.json vars): default 가 있으면 그것, 없으면 종류별 (int 0 · bool false · list [] · 그 밖 "")
static func var_default(cfg: CareerConfig, id: String) -> Variant:
	var d: Dictionary = cfg.vars.get(id, {})
	if d.has("default"):
		return (d["default"] as Array).duplicate() if d["default"] is Array else d["default"]
	match str(d.get("type", "")):
		"int":
			return 0
		"bool":
			return false
		"list":
			return []
	return ""


## 변수 값: overlay(대화 도중 고른 선택의 효과 — 아직 반영 전) → 선수 vars → 기본값
static func var_of(career: CareerState, id: String, overlay: Dictionary = {}) -> Variant:
	if overlay.has(id):
		return overlay[id]
	if career.player.vars.has(id):
		return career.player.vars[id]
	return var_default(career.cfg, id)


## 효과 하나가 변수에 주는 변화를 overlay 에 미리 적는다 (대화 도중 분기가 방금 고른 선택을 보게)
static func note_vars(career: CareerState, fx: Dictionary, overlay: Dictionary) -> void:
	for id: String in fx.get("set", {}):
		overlay[id] = fx["set"][id]
	for id: String in fx.get("add", {}):
		overlay[id] = int(var_of(career, id, overlay)) + int(fx["add"][id])


## 조건 검사. cond: {"var": id, eq|ne|gte|lte|gt|lt|in: 값} · {"flag": f} · {"fired": 이벤트 id}
##   · {"all": [조건…]} · {"any": [조건…]} · {"not": 조건}. 빈 조건은 참
static func test(career: CareerState, cond: Dictionary, overlay: Dictionary = {}) -> bool:
	if cond.has("all"):
		for c: Dictionary in cond["all"]:
			if not test(career, c, overlay):
				return false
		return true
	if cond.has("any"):
		for c: Dictionary in cond["any"]:
			if test(career, c, overlay):
				return true
		return false
	if cond.has("not"):
		return not test(career, cond["not"], overlay)
	if cond.has("flag"):
		return career.player.flags.has(cond["flag"])
	if cond.has("fired"):
		return career.book.fired.has(cond["fired"])
	if cond.has("lastResult"):
		return career.last.get("result", "") == cond["lastResult"]
	if not cond.has("var"):
		return true
	var v: Variant = var_of(career, cond["var"], overlay)
	# 비교값 {"const": 이름} 은 balance.json career.story.constants 의 수 (임계값을 콘텐츠에 박지 않는다)
	for k: String in COMPARES:
		if cond.has(k) and cond[k] is Dictionary and cond[k].has("const"):
			cond = cond.duplicate()
			cond[k] = career.cfg.story["constants"][cond[k]["const"]]
	if cond.has("eq"):
		return _same(v, cond["eq"])
	if cond.has("ne"):
		return not _same(v, cond["ne"])
	if cond.has("in"):
		for x in cond["in"]:
			if _same(v, x):
				return true
		return false
	var n := float(v) if (v is int or v is float) else 0.0
	if cond.has("gte"):
		return n >= float(cond["gte"])
	if cond.has("lte"):
		return n <= float(cond["lte"])
	if cond.has("gt"):
		return n > float(cond["gt"])
	if cond.has("lt"):
		return n < float(cond["lt"])
	return true


## JSON 숫자는 실수로 읽히므로 숫자끼리는 값으로 비교한다
static func _same(a: Variant, b: Variant) -> bool:
	if (a is int or a is float) and (b is int or b is float):
		return float(a) == float(b)
	return typeof(a) == typeof(b) and a == b


## 조건에 나오는 변수 이름들 (검사용)
static func cond_vars(cond: Dictionary, out: Array = []) -> Array:
	for k in ["all", "any"]:
		for c: Dictionary in cond.get(k, []):
			cond_vars(c, out)
	if cond.has("not"):
		cond_vars(cond["not"], out)
	if cond.has("var"):
		out.append(cond["var"])
	return out


## 지금 챕터 이름 ("1학년 · 신입생")
static func chapter(career: CareerState) -> String:
	return career.cfg.chapters.get(str(career.player.grade), "")


# ---------- 무결성 검사 (테스트용) ----------

## 어딘가에서 세워지는 플래그들 (이벤트 선택지·대본의 do · 문자 답장 · pendingFlags) — requires 검사용
static func known_flags(cfg: CareerConfig) -> Dictionary:
	var flags := {}
	for f: String in cfg.pending_flags:
		flags[f] = true
	for e: Dictionary in cfg.events:
		for fx: Dictionary in all_effects(e):
			for f in _list(fx.get("flag", [])):
				flags[f] = true
	for m: Dictionary in cfg.messages:
		for fx: Dictionary in [m.get("ignore", {})] + (m.get("replies", []) as Array).map(func(r: Dictionary) -> Dictionary: return r.get("effects", {})):
			for f in _list(fx.get("flag", [])):
				flags[f] = true
	return flags


## 이벤트 대본 안의 모든 효과 사전 (선택지 · 확률 갈래 · do 줄)
static func all_effects(e: Dictionary) -> Array:
	var found := {"choices": [], "lines": []}
	_collect(script_of(e), found)
	var out := []
	for c: Dictionary in found["choices"]:
		out.append_array(_choice_effects(c))
	out.append_array(found.get("dos", []))
	return out


## 대사에 쓸 수 있는 글자 치환 이름들
static func token_names(cfg: CareerConfig) -> Array:
	var out := ["player", "school", "rivalSchool", "scoutTeam", "rivalLast", "pick", "rank", "partner", "partnerName", "phaseLine"]
	for id: String in cfg.people:
		out.append(id)
		out.append(id + "Name")
	out.append_array(cfg.vars.keys())
	return out


## 이 미니게임 id 를 띄울 수 있나: 체력 테스트(전체·종목 하나) 또는 drill:<연습 id> (content.json drills)
static func minigame_ok(cfg: CareerConfig, id: String) -> bool:
	if id in FitnessTest.IDS:
		return cfg.minigames.has(FitnessTest.ID)
	return id.begins_with("drill:") and cfg.drills.has(id.trim_prefix("drill:"))


## 조건 하나 검사 ("" 이면 통과): 변수는 선언돼 있어야 하고, enum 값은 values 안에 있어야 한다
static func _cond_error(cfg: CareerConfig, cond: Variant) -> String:
	if not cond is Dictionary:
		return "조건 형식"
	for v: String in cond_vars(cond):
		if not cfg.vars.has(v):
			return "모르는 변수 %s" % v
	var c: Dictionary = cond
	if c.has("var"):
		var keys := c.keys().filter(func(k: String) -> bool: return k in COMPARES)
		if keys.size() != 1:
			return "%s 비교는 %s 중 하나" % [c["var"], "|".join(COMPARES)]
		var vals: Array = c["in"] if c.has("in") else [c[keys[0]]]
		if vals[0] is Dictionary:
			return "" if cfg.story.get("constants", {}).has(vals[0].get("const", "")) else "모르는 상수 %s" % vals[0]
		for x in vals:
			var why := _value_error(cfg, c["var"], x)
			if why != "":
				return why
	return ""


## 변수에 이 값을 넣거나 비교해도 되나 ("" 이면 통과)
static func _value_error(cfg: CareerConfig, id: String, value: Variant) -> String:
	var d: Dictionary = cfg.vars.get(id, {})
	if d.get("type", "") == "enum" and not value in d.get("values", []):
		return "%s 에 없는 값 %s" % [id, value]
	return ""


## 대본 노드 구조 검사 (branch 의 갈래, 선택 지점의 if·id)
static func _nodes_error(cfg: CareerConfig, nodes: Array) -> String:
	for node: Dictionary in nodes:
		if node.has("branch"):
			var arms: Array = node["branch"]
			if arms.is_empty():
				return "branch 가 비었다"
			for i in arms.size():
				var arm: Dictionary = arms[i]
				if not arm.has("if") and i != arms.size() - 1:
					return "branch 의 조건 없는 갈래(else)는 맨 끝에만"
				if arm.has("if"):
					var why := _cond_error(cfg, arm["if"])
					if why != "":
						return why
				var inner := _nodes_error(cfg, arm.get("then", []))
				if inner != "":
					return inner
		elif node.has("choices"):
			if node.has("id") and not cfg.vars.has(node["id"]):
				return "선택 지점 id %s 가 변수로 선언되지 않았다" % node["id"]
			for c: Dictionary in node["choices"]:
				if c.has("if"):
					var why := _cond_error(cfg, c["if"])
					if why != "":
						return why
				var inner := _nodes_error(cfg, c.get("then", []))
				if inner != "":
					return inner
				for o: Dictionary in c.get("outcomes", []):
					inner = _nodes_error(cfg, o.get("then", []))
					if inner != "":
						return inner
	return ""


## 경기 대본(gamePlan 효과) 검사 ("" 이면 통과)
static func _plan_error(cfg: CareerConfig, v: Variant, event_ids: Dictionary) -> String:
	if not v is Dictionary:
		return "형식"
	var plan: Dictionary = v
	for k: String in plan:
		if not k in ["opponent", "practice", "home", "innings", "role", "line", "scenes", "after"]:
			return "모르는 키 %s" % k
	var n := int(plan.get("innings", cfg.innings))
	if n < 2 or n > cfg.innings:
		return "innings 는 2~%d" % cfg.innings
	if not plan.get("role", "start") in ["start", "sub"]:
		return "role 은 start | sub"
	if not plan.get("after", "fixed") in ["fixed", "play"]:
		return "after 는 fixed | play"
	if plan.has("opponent"):
		var known := false
		for s: Dictionary in cfg.regional_schools + cfg.national_schools + cfg.exhibition_schools:
			known = known or s["id"] == plan["opponent"]
		if not known:
			return "상대 학교 %s 없음" % plan["opponent"]
	var line: Array = plan.get("line", [[], []])
	if line.size() != 2 or (line[0] as Array).size() > n or (line[1] as Array).size() > n:
		return "line 은 [[우리 이닝별 점수…], [상대 이닝별 점수…]] (이닝 수 이하)"
	var scenes: Array = plan.get("scenes", [])
	if scenes.is_empty():
		return "scenes 가 비었다"
	var last := -1
	for sc: Dictionary in scenes:
		for k: String in sc:
			if not k in ["inning", "half", "outs", "bases", "score", "slot", "kind", "event", "var", "map", "pitcher", "boost", "doublePlay"]:
				return "장면 모르는 키 %s" % k
		if not sc.get("kind", "") in ["pa", "field"]:
			return "장면 kind 는 pa | field"
		if not sc.get("half", "") in ["top", "bottom"] or int(sc.get("inning", 0)) < 1 or int(sc["inning"]) > n:
			return "장면 inning·half"
		var at := (int(sc["inning"]) - 1) * 2 + (0 if sc["half"] == "top" else 1)
		if at <= last:
			return "장면은 경기 순서대로, 한 반 이닝에 하나"
		last = at
		if int(sc.get("outs", 0)) < 0 or int(sc.get("outs", 0)) > 2:
			return "장면 outs 는 0~2"
		for b in sc.get("bases", []):
			if int(b) < 1 or int(b) > 3:
				return "장면 bases 는 1~3"
		if sc.has("score") and (not sc["score"] is Array or (sc["score"] as Array).size() != 2):
			return "장면 score 는 [우리, 상대]"
		if sc.has("event") and not event_ids.has(sc["event"]):
			return "장면 이벤트 %s 없음" % sc["event"]
		if sc.has("pitcher") and not sc["pitcher"] in Team.PITCHER_PROFILES:
			return "pitcher 는 %s" % "|".join(Team.PITCHER_PROFILES)
		if sc.has("var"):
			if not cfg.vars.has(sc["var"]):
				return "장면 변수 %s 없음" % sc["var"]
			for key: String in sc.get("map", {}):
				var val: Variant = sc["map"][key]
				var vals: Array = [val.get("then"), val.get("else")] if val is Dictionary else [val]
				if val is Dictionary:
					var why := _cond_error(cfg, val.get("if", {}))
					if why != "":
						return why
				for x in vals:
					var why := _value_error(cfg, sc["var"], x)
					if why != "":
						return why
		for st: String in sc.get("boost", {}):
			if not st in PlayerData.STATS:
				return "장면 boost 능력치 %s" % st
	return ""


## 이벤트 정의 검사. 돌려주는 값: 문제 설명 목록 (비었으면 통과)
static func validate(cfg: CareerConfig) -> Array[String]:
	var errors: Array[String] = []
	var ids := {}
	var flags_set := known_flags(cfg)
	var schedule_targets := {}
	var event_ids := {}
	for e: Dictionary in cfg.events:
		event_ids[e.get("id", "")] = true
		for fx: Dictionary in all_effects(e):
			if fx.has("schedule"):
				schedule_targets[fx["schedule"].get("event", "")] = true
	for m: Dictionary in cfg.messages:
		for r: Dictionary in m.get("replies", []):
			if (r.get("effects", {}) as Dictionary).has("schedule"):
				schedule_targets[r["effects"]["schedule"].get("event", "")] = true
	var known_tokens := token_names(cfg)
	var people_ids: Array = cfg.people.keys()
	people_ids.append("partner")
	for vid: String in cfg.vars:
		var d: Dictionary = cfg.vars[vid]
		if not d.get("type", "") in ["int", "bool", "enum", "string", "list"]:
			errors.append("변수 %s: type 은 int|bool|enum|string|list" % vid)
		if d.get("type", "") == "enum" and (d.get("values", []) as Array).is_empty():
			errors.append("변수 %s: enum 은 values 가 있어야 한다" % vid)
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
		if str(e.get("speaker", "")) == "" or (str(e.get("text", "")) == "" and not e.has("script")):
			errors.append("%s: speaker·text 가 비었다" % where)
		if e.has("script") and e.has("text"):
			errors.append("%s: script 와 text 를 같이 쓸 수 없다" % where)
		if not e.has("script") and (e.get("choices", []) as Array).is_empty():
			errors.append("%s: 옛 형식(text) 이벤트는 선택지가 있어야 한다" % where)
		if (timing_of(e) == "game") != e.has("moment") or (e.has("moment") and not e["moment"] in MOMENTS):
			errors.append("%s: timing game 이벤트는 moment(%s)가 있어야 하고, 다른 이벤트엔 없어야 한다" % [where, ", ".join(MOMENTS)])
		if e.has("winter") and not (e["winter"] is bool or e["winter"] == "any"):
			errors.append("%s: winter 는 true 또는 any" % where)
		if e.has("campWeek") and e.get("winter", false) is not bool:
			errors.append("%s: campWeek 는 winter: true 이벤트에서만" % where)
		for k in ["grade", "week", "campWeek"]:
			if e.has(k) and ((e[k] as Array).size() != 2 or int(e[k][0]) > int(e[k][1])):
				errors.append("%s: %s 범위" % [where, k])
		for f: String in e.get("requires", []):
			if not flags_set.has(f):
				errors.append("%s: requires %s 를 세우는 선택지가 없다" % [where, f])
		for a: String in e.get("after", []):
			if not event_ids.has(a):
				errors.append("%s: after 이벤트 %s 없음" % [where, a])
		if e.has("when"):
			var why := _cond_error(cfg, e["when"])
			if why != "":
				errors.append("%s: when %s" % [where, why])
		var nodes_why := _nodes_error(cfg, script_of(e))
		if nodes_why != "":
			errors.append("%s: 대본 %s" % [where, nodes_why])
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
		if e.has("stage"):
			var why := _stage_error(cfg, e)
			if why != "":
				errors.append("%s: stage %s" % [where, why])
		if e.get("moment", "") == "scene" and (e.has("stage") or e.get("repeat", false) or not e.get("story", false)):
			errors.append("%s: scene 이벤트는 한 번뿐인 스토리(story, repeat 아님)이고 stage 를 쓰지 않는다" % where)
		if (e.has("recentMinHits") or e.has("recentMaxHits")) != e.has("recentGames") or int(e.get("recentGames", 1)) <= 0:
			errors.append("%s: recentGames(1 이상)는 recentMinHits·recentMaxHits 와 같이 쓴다" % where)
		if e.has("lastResult") and not e["lastResult"] in ["win", "loss", "draw"]:
			errors.append("%s: lastResult %s" % [where, e["lastResult"]])
		if e.get("scheduledOnly", false) and not schedule_targets.has(id):
			errors.append("%s: scheduledOnly 인데 예약하는 선택지가 없다" % where)
		if not cfg.people.has(e.get("speaker", "")) and str(e.get("speaker", "")).begins_with("{"):
			errors.append("%s: speaker" % where)
		var texts: Array = []
		var found := {"choices": [], "lines": []}
		_collect(script_of(e), found)
		for line: Dictionary in found["lines"]:
			texts.append(line.get("text", ""))
			if str(line.get("text", "")) == "":
				errors.append("%s: 빈 대사·결과" % where)
			var sp: String = line.get("speaker", "")
			# 영문 id 처럼 생겼는데 인물이 아니면 오타로 본다 (인물이 아닌 화자는 한글 이름으로 쓴다)
			if sp != "" and sp != "player" and not sp in people_ids and _is_ascii(sp):
				errors.append("%s: 대사 화자 %s 없음" % [where, sp])
			if line.has("face") and not line["face"] in FACES:
				errors.append("%s: 모르는 표정 %s" % [where, line["face"]])
			if line.has("ui") and not line["ui"] in UI_KINDS:
				errors.append("%s: 모르는 화면 표시 ui %s" % [where, line["ui"]])
			if sp == "partner" and e.get("partner", "") != "any":
				errors.append("%s: partner 가 말하면 partner: any 조건이 있어야 한다" % where)
		for c: Dictionary in found["choices"]:
			texts.append(c.get("label", ""))
			if str(c.get("label", "")) == "":
				errors.append("%s: 선택지 label 이 비었다" % where)
			if c.has("cost") and int(c["cost"]) <= 0:
				errors.append("%s: cost 는 양수" % where)
			if c.has("outcomes"):
				if (c["outcomes"] as Array).is_empty():
					errors.append("%s: outcomes 가 비었다" % where)
				for o: Dictionary in c["outcomes"]:
					if float(o.get("weight", 0)) <= 0.0:
						errors.append("%s: outcome 의 weight" % where)
		for fx: Dictionary in all_effects(e):
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
			for k in ["meet", "addContact"]:
				for pid in _list(fx.get(k, [])):
					if not cfg.people.has(pid):
						errors.append("%s: %s 인물 %s 없음" % [where, k, pid])
			if fx.has("schedule"):
				var target: String = fx["schedule"].get("event", "")
				if not (_has_event(cfg, target) or target in cfg.pending_events) or int(fx["schedule"].get("weeks", 0)) <= 0:
					errors.append("%s: schedule 대상 %s" % [where, target])
			if fx.has("campFocus") and not (fx["campFocus"] in PlayerData.STATS or fx["campFocus"] == CareerState.CAMP_FOCUS_BODY):
				errors.append("%s: campFocus %s" % [where, fx["campFocus"]])
			if fx.has("lineupSlot") and (int(fx["lineupSlot"]) < 0 or int(fx["lineupSlot"]) > 9):
				errors.append("%s: lineupSlot 은 0(벤치)~9" % where)
			if fx.has("matchOverride"):
				var why := _match_override_error(cfg, fx["matchOverride"])
				if why != "":
					errors.append("%s: matchOverride %s" % [where, why])
			if fx.has("gamePlan"):
				var why := _plan_error(cfg, fx["gamePlan"], event_ids)
				if why != "":
					errors.append("%s: gamePlan %s" % [where, why])
			for st: String in fx.get("gameBoost", {}):
				if not st in PlayerData.STATS:
					errors.append("%s: gameBoost 능력치 %s" % [where, st])
			for st: String in fx.get("train", {}):
				if not st in PlayerData.STATS:
					errors.append("%s: train 능력치 %s" % [where, st])
			if fx.has("minigame") and not minigame_ok(cfg, str(fx["minigame"])):
				errors.append("%s: minigame %s 없음" % [where, fx["minigame"]])
			for k in ["set", "add"]:
				for vid: String in fx.get(k, {}):
					if not cfg.vars.has(vid):
						errors.append("%s: %s 변수 %s 가 선언되지 않았다" % [where, k, vid])
					elif k == "set":
						var why := _value_error(cfg, vid, fx["set"][vid])
						if why != "":
							errors.append("%s: set %s" % [where, why])
					elif cfg.vars[vid].get("type", "") != "int":
						errors.append("%s: add 는 int 변수만 (%s)" % [where, vid])
			if fx.has("gear") and not (cfg.shop["gear"] as Dictionary).has(fx["gear"]):
				errors.append("%s: gear %s 없음" % [where, fx["gear"]])
			if fx.has("path") and not fx["path"] in PATHS:
				errors.append("%s: path %s" % [where, fx["path"]])
		for t: String in texts:
			for tok in _tokens(t):
				if not tok in known_tokens:
					errors.append("%s: 모르는 글자 치환 {%s}" % [where, tok])
	return errors


## stage(경기 장면 연출) 검사 ("" 이면 통과)
static func _stage_error(cfg: CareerConfig, e: Dictionary) -> String:
	if timing_of(e) != "game" or not e.get("story", false) or e.get("repeat", false):
		return "은 한 번뿐인 경기 중 스토리(timing game, story, repeat 아님)에만"
	if not e["stage"] is Dictionary:
		return "형식"
	var s: Dictionary = e["stage"]
	for k: String in s:
		if not k in ["inning", "half", "outs", "bases", "score", "slot", "runnerSlots", "reached", "practice", "pitcher"]:
			return "모르는 키 %s" % k
	if not s.get("half", "") in ["top", "bottom"]:
		return "half 는 top 또는 bottom"
	if int(s.get("inning", 0)) < 2 or int(s.get("inning", 0)) > cfg.innings:
		return "inning 은 2~%d" % cfg.innings
	if not s.get("score") is Array or (s["score"] as Array).size() != 2 or int(s["score"][0]) < 0 or int(s["score"][1]) < 0:
		return "score 는 [우리 점수, 상대 점수]"
	if s["half"] == "bottom" and int(s["inning"]) == cfg.innings and int(s["score"][0]) > int(s["score"][1]):
		return "마지막 회 말에 이기고 있으면 공격이 없다"
	if int(s.get("outs", 0)) < 0 or int(s.get("outs", 0)) > 2:
		return "outs 는 0~2"
	var bases: Array = s.get("bases", [])
	var seen := {}
	for b in bases:
		if int(b) < 1 or int(b) > 3 or seen.has(int(b)):
			return "bases 는 1~3 을 한 번씩"
		seen[int(b)] = true
	if (s.get("runnerSlots", []) as Array).size() > bases.size() or (s.get("reached", []) as Array).size() > bases.size():
		return "runnerSlots·reached 는 bases 와 같은 순서로, 그보다 길 수 없다"
	for v in s.get("runnerSlots", []) + ([s["slot"]] if s.has("slot") else []):
		if int(v) < 1 or int(v) > 9:
			return "타순은 1~9"
	if s.has("pitcher") and not s["pitcher"] in Team.PITCHER_PROFILES:
		return "pitcher 는 %s" % "|".join(Team.PITCHER_PROFILES)
	for v in s.get("reached", []):
		if not v in ["walk", "single", "double", "triple"]:
			return "reached 는 walk|single|double|triple"
	return ""


## matchOverride 효과 검사 ("" 이면 통과)
static func _match_override_error(cfg: CareerConfig, v: Variant) -> String:
	if not v is Dictionary:
		return "형식"
	var m: Dictionary = v
	for k: String in m:
		if not k in ["opponent", "result", "score", "innings", "playerAbsent", "home", "line", "rivalLine"]:
			return "모르는 키 %s" % k
	if not m.get("score") is Array or (m["score"] as Array).size() != 2 or int(m["score"][0]) < 0 or int(m["score"][1]) < 0:
		return "score 는 [우리 점수, 상대 점수]"
	var want: String = ["loss", "draw", "win"][signi(int(m["score"][0]) - int(m["score"][1])) + 1]
	if m.get("result", want) != want:
		return "result 가 score 와 다르다"
	if not m.get("playerAbsent", true):
		return "playerAbsent 는 true 만 (결과를 고정한 경기에 내 선수는 나오지 않는다)"
	if int(m.get("innings", cfg.innings)) < 1 or int(m.get("innings", cfg.innings)) > cfg.innings:
		return "innings 는 1~%d" % cfg.innings
	if m.has("line"):
		var n := int(m.get("innings", cfg.innings))
		if not m["line"] is Array or (m["line"] as Array).size() != 2:
			return "line 은 [[우리 이닝별 점수…], [상대 이닝별 점수…]]"
		for i in 2:
			var per: Array = m["line"][i]
			var sum := 0
			for run in per:
				sum += int(run)
			if sum != int(m["score"][i]) or per.size() > n or per.size() < n - 1:
				return "line 의 합·이닝 수가 score·innings 와 다르다"
	for k: String in m.get("rivalLine", {}):
		if not k in ["ab", "hits", "doubles", "homeRuns", "rbi", "walks", "strikeouts"]:
			return "rivalLine 모르는 키 %s" % k
	if m.has("opponent"):
		var known := false
		for s: Dictionary in cfg.regional_schools + cfg.national_schools:
			if s["id"] == m["opponent"]:
				known = true
		if not known:
			return "상대 학교 %s 없음" % m["opponent"]
	return ""


## 선택지 하나의 효과 사전들 (자기 효과 + 확률 갈래들의 효과)
static func _choice_effects(c: Dictionary) -> Array:
	var out := [c.get("effects", {})]
	for o: Dictionary in c.get("outcomes", []):
		out.append(o.get("effects", {}))
	return out


## 대본을 끝까지 훑어 모든 줄과 선택지를 모은다 (then·outcomes 안쪽까지)
static func _collect(nodes: Array, found: Dictionary) -> void:
	for node: Dictionary in nodes:
		if node.has("branch"):
			found.get_or_add("branches", []).append(node)
			for arm: Dictionary in node["branch"]:
				_collect(arm.get("then", []), found)
			continue
		if node.has("do"):
			found.get_or_add("dos", []).append(node["do"])
			continue
		if not node.has("choices"):
			found["lines"].append(node)
			continue
		for c: Dictionary in node["choices"]:
			found["choices"].append(c)
			_collect(c.get("then", []), found)
			for o: Dictionary in c.get("outcomes", []):
				_collect(o.get("then", []), found)


static func _is_ascii(t: String) -> bool:
	for i in t.length():
		if t.unicode_at(i) > 127:
			return false
	return true


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
