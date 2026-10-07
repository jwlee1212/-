class_name People
extends RefCounted
## 고정 인물 (content.json people): 감독·엄마·동기·라이벌·스카우트 + 연애 후보(매니저·소꿉친구·방송부).
## 인물마다 관계 수치(0~100)와 만났는지가 있다. 관계는 이벤트 조건(minRel·maxRel)과 새 시즌 타순(감독 신뢰)에 쓰인다.
## 연애 후보의 관계는 호감. 사귀는 사람(partner)은 한 명 — 이벤트에서 "partner" 라고 부르면 그 사람이다.


class Person:
	var id: String
	var role: String
	## 뽑은 이름 (엄마는 "엄마")
	var name: String
	## 이벤트·말풍선에서 부르는 이름 ("박태수 감독")
	var call: String
	var desc: String
	var rel_label: String
	var relation := 50
	var met := true
	## 라이벌의 학교 id, 스카우트의 구단 이름 (없으면 "")
	var school_id := ""
	var team_name := ""
	## 연애 후보인가
	var romance := false
	## 이 사람이 말하는 이벤트의 기본 배경 (content.json places id)
	var place := ""


## id -> Person (content.json 순서 유지)
var by_id := {}
## 사귀는 사람 id ("" 없음)
var partner := ""


## extra: {"rival": 라이벌 이름, "scoutTeam": 스카우트 구단 이름}
static func create(cfg: CareerConfig, rng: RandomNumberGenerator, extra: Dictionary) -> People:
	var ps := People.new()
	var starts: Dictionary = cfg.story["relationStart"]
	for id: String in cfg.people:
		var d: Dictionary = cfg.people[id]
		var p := Person.new()
		p.id = id
		p.role = d["role"]
		if d.has("fixedName"):
			p.name = d["fixedName"]
		elif id == "rival" and extra.has("rival"):
			p.name = extra["rival"]
		else:
			p.name = Team._random_name(cfg, rng)
		p.call = str(d["callFormat"]).replace("{name}", p.name)
		p.desc = d["desc"]
		p.rel_label = d["relLabel"]
		p.relation = int(starts.get(id, 50))
		p.met = not d.get("hiddenUntilMet", false)
		p.school_id = d.get("school", "")
		p.romance = d.get("romance", false)
		p.place = d.get("place", "")
		if id == "scout":
			p.team_name = extra.get("scoutTeam", "")
		ps.by_id[id] = p
	return ps


## "partner" 는 지금 사귀는 사람 (없으면 null)
func person(id: String) -> Person:
	if id == "partner":
		return by_id.get(partner)
	return by_id.get(id)


func has(id: String) -> bool:
	return person(id) != null


func relation(id: String) -> int:
	var p := person(id)
	return p.relation if p != null else 0


func add_relation(id: String, delta: int) -> void:
	var p := person(id)
	if p != null:
		p.relation = clampi(p.relation + delta, 0, 100)


func meet(id: String) -> void:
	by_id[id].met = true


func is_met(id: String) -> bool:
	return by_id[id].met


## 관계를 말로 (인물 탭)
static func relation_text(v: int) -> String:
	if v >= 80:
		return "끈끈하다"
	if v >= 60:
		return "좋다"
	if v >= 40:
		return "그럭저럭"
	if v >= 20:
		return "서먹하다"
	return "냉랭하다"
