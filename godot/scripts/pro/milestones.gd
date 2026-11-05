class_name Milestones
extends RefCounted
## 프로 편 기념 순간: 프로 데뷔 뒤로는 이야기 대신 "상황"이 컷신을 연다 (첫 안타·첫 홈런·첫 타점·데뷔·가을야구·우승·상 …).
## 정의는 content.json pro.milestones: [{"id", "title", "line", "cutscene", "when": {...}}]
##   when.kind = "career" (통산 1군 기록 stat ≥ gte) | "season" (이번 시즌 stat ≥ gte) | "game" (한 경기 stat ≥ gte)
##             | "event" (event: debut · callup · post_debut · ks_champion) | "award" (award: 상 이름)
## 기념 순간은 커리어에 한 번. 생기면 ProCareer.pending_scenes 에 쌓이고, 프로 허브가 컷신(있으면) 또는 컷신 자리 카드로 보여 준다.
## 그림이 아직 없는 컷신은 "컷신 자리" 카드로 대신한다 — 목록은 docs/scenario/pro-cutscene-list.md.

const KINDS := ["career", "season", "game", "event", "award"]
const STATS := ["hits", "home_runs", "rbi", "steals", "walks", "doubles", "triples", "games"]
const EVENTS := ["debut", "callup", "post_debut", "ks_champion", "enlist", "discharge", "exempt", "national", "medal_asian", "medal_olympic",
	"fa_stay", "fa_move"]


## 이번 주 경기들(lines: 내 경기 줄들)을 보고 새 기념 순간을 쌓는다. 1군 기록만 센다 (퓨처스 기록은 안 센다)
static func check(career: CareerState, pro: ProCareer, lines: Array) -> void:
	for m: Dictionary in career.cfg.pro_content.get("milestones", []):
		if pro.milestones_done.has(m["id"]):
			continue
		var w: Dictionary = m["when"]
		var hit := false
		match w["kind"]:
			"career":
				var total := PlayerData.SeasonStats.new()
				total.merge(pro.me.career)
				total.merge(pro.me.season)
				hit = _stat(total, w["stat"]) >= int(w["gte"])
			"season":
				hit = _stat(pro.me.season, w["stat"]) >= int(w["gte"])
			"game":
				if pro.is_first() or pro.phase != ProCareer.PHASE_REGULAR:
					for ln: PlayerData.SeasonStats in lines:
						if ln != null and _stat(ln, w["stat"]) >= int(w["gte"]):
							hit = true
		if hit:
			_fire(career, pro, m)


static func fire_event(career: CareerState, pro: ProCareer, event: String) -> void:
	for m: Dictionary in career.cfg.pro_content.get("milestones", []):
		if m["when"]["kind"] == "event" and m["when"]["event"] == event and not pro.milestones_done.has(m["id"]):
			_fire(career, pro, m)


static func fire_award(career: CareerState, pro: ProCareer, award: String) -> void:
	for m: Dictionary in career.cfg.pro_content.get("milestones", []):
		if m["when"]["kind"] == "award" and m["when"]["award"] == award and not pro.milestones_done.has(m["id"]):
			_fire(career, pro, m)


static func _fire(career: CareerState, pro: ProCareer, m: Dictionary) -> void:
	pro.milestones_done[m["id"]] = pro.league.year
	pro.pending_scenes.append(m)
	career.player.flags["ms_" + m["id"]] = true
	pro.fame = mini(100, pro.fame + int(m.get("fame", 2)))
	pro.league.week_events.append({"kind": "milestone", "title": m["title"], "id": m["id"]})


static func _stat(s: PlayerData.SeasonStats, stat: String) -> int:
	match stat:
		"hits": return s.hits
		"home_runs": return s.home_runs
		"rbi": return s.rbi
		"steals": return s.steals
		"walks": return s.walks
		"doubles": return s.doubles
		"triples": return s.triples
		"games": return s.games
	return 0


## 컷신이 그려져 있나 (content.json cutscenes 에 그 id 가 있으면 진짜 컷신, 없으면 컷신 자리 카드)
static func has_cutscene(cfg: CareerConfig, m: Dictionary) -> bool:
	return cfg.cutscenes.has(m.get("cutscene", ""))


static func validate(cfg: CareerConfig) -> Array[String]:
	var errors: Array[String] = []
	var ids := {}
	for m: Dictionary in cfg.pro_content.get("milestones", []):
		var id: String = m.get("id", "")
		if id == "" or ids.has(id):
			errors.append("기념 순간 id 가 없거나 겹친다: %s" % id)
		ids[id] = true
		for k in ["title", "line", "cutscene"]:
			if str(m.get(k, "")) == "":
				errors.append("기념 순간 %s: %s 가 비었다" % [id, k])
		var w: Dictionary = m.get("when", {})
		var kind: String = w.get("kind", "")
		if not kind in KINDS:
			errors.append("기념 순간 %s: when.kind %s" % [id, kind])
		elif kind in ["career", "season", "game"] and (not w.get("stat", "") in STATS or int(w.get("gte", 0)) <= 0):
			errors.append("기념 순간 %s: stat/gte" % id)
		elif kind == "event" and not w.get("event", "") in EVENTS:
			errors.append("기념 순간 %s: event %s" % [id, w.get("event", "")])
		elif kind == "award" and str(w.get("award", "")) == "":
			errors.append("기념 순간 %s: award 가 비었다" % id)
	return errors
