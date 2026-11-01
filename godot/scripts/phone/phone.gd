class_name Phone
extends RefCounted
## 폰의 문자 (content.json messages, 형식은 phone._note). 로직만 — 화면은 PhoneView 가 그린다.
##
## 문자는 "일러스트 없는 가벼운 이벤트"다: 조건은 돌발 이벤트와 같은 키를 같은 검사기(EventBook.eligible)로 보고,
## 답장의 효과도 같은 처리기(EventBook.apply_effects)로 반영한다.
##   - 새 주가 시작될 때(deliver) 조건이 맞는 문자가 content 순서대로 온다. 한 주에 maxPerWeek 통까지, 문자 하나는 커리어에 한 번.
##   - 답장(replies)이 있는 문자는 replyWeeks 주 안에 답할 수 있다. 답장은 훈련·교류 같은 주간 행동을 쓰지 않는다.
##   - 안 하면 "답장할 때를 놓쳤다"로 닫힌다 (ignore 효과가 있으면 그때 반영). 답장을 기다리는 방에는 다음 문자가 오지 않는다.
##   - 난수는 쓰지 않는다 — 같은 진행이면 같은 문자가 같은 주에 온다 (원칙 2).
## 방(room)은 인물 하나(인물 id) 또는 단체방(content.json phone.rooms 의 id).

const KEYS := ["id", "speaker", "room", "text", "lines", "replies", "ignore", "replyWeeks",
	"grade", "week", "requires", "forbids", "met", "forbidsMet", "minScout", "maxScout", "minRel", "maxRel", "minRep", "maxRep",
	"maxForm", "maxCondition", "injured", "partner", "minMoney", "winter", "campWeek",
	"recentGames", "recentMinHits", "recentMaxHits",
	"lastResult", "lastMinHits", "lastMaxHits", "lastMinHomeRuns", "lastPhaseEnd", "lastNational", "lastOpponent", "seasonOver"]
## 문자 답장에 쓸 수 있는 효과 (가벼운 것만 — 타순·부상·경기 결과 같은 큰 변화는 대화 이벤트에서, schedule 로 이어 준다)
const EFFECTS := ["condition", "form", "reputation", "money", "rel", "meet", "flag", "clearFlag", "schedule"]
## 보낸 사람: 내가 보낸 답장, 안내 줄(답장할 때를 놓쳤다 · 변화 설명)
const ME := "me"
const SYSTEM := ""
## 문자 상태: 답장을 기다린다 · 답장했다 · 놓쳤다 · 답장이 없는 문자
const OPEN := "open"
const REPLIED := "replied"
const EXPIRED := "expired"
const INFO := "info"

## 방 id -> 줄 [{"from": 인물 id | ME | SYSTEM, "text", "tw": 받은 주(total_week), "msg": 문자 id}]
var rooms := {}
## 방 순서 (마지막으로 문자가 온 방이 앞)
var order: Array[String] = []
## 방 id -> 안 읽은 줄 수
var unread := {}
## 문자 id -> {"state", "room", "tw": 받은 주, "due": 이 주부터는 답장 못 한다, "choice": 고른 답장 번호(-1 안 고름)}
var status := {}
## 아직 허브에서 알리지 않은 새 문자 id (알림을 띄우면 비운다)
var fresh: Array[String] = []
## 연락처에서 마지막으로 본 관계 문구 (인물 id -> 문구). 지금 문구와 다르면 "바뀌었다"고 표시한다
var seen_relation := {}
## 연락처에 있는 인물 id (추가한 순서). 엄마는 처음부터, 나머지는 이야기가 해금할 때(이벤트 효과 contact) 들어온다
var contacts: Array[String] = []


# ---------- 받기 ----------

## 새 주가 시작됐다: 답장 시기가 지난 문자를 닫고, 조건이 맞는 새 문자를 받는다
func deliver(career: CareerState) -> void:
	_expire(career)
	var room_left := int(career.cfg.phone["maxPerWeek"])
	for m: Dictionary in career.cfg.messages:
		if room_left <= 0:
			break
		if status.has(m["id"]) or not eligible(career, m):
			continue
		_receive(career, m)
		room_left -= 1


## 이 문자가 지금 올 수 있나: 보낸 사람(단체방이면 말하는 사람 모두)을 만났고, 그 방이 답장을 기다리는 중이 아니고, 이벤트와 같은 조건이 맞는다
func eligible(career: CareerState, m: Dictionary) -> bool:
	for id: String in senders(m):
		if not career.people.is_met(id):
			return false
	if open_message(room_of(m)) != "":
		return false
	return career.book.eligible(career, m)


func _receive(career: CareerState, m: Dictionary) -> void:
	var room := room_of(m)
	var tw := career.total_week
	for line: Dictionary in lines_of(m):
		_add(room, line.get("from", m["speaker"]), EventBook.fill(career, line["text"]), tw, m["id"])
	unread[room] = int(unread.get(room, 0)) + lines_of(m).size()
	var has_reply := not (m.get("replies", []) as Array).is_empty()
	status[m["id"]] = {"state": OPEN if has_reply else INFO, "room": room, "tw": tw,
		"due": tw + int(m.get("replyWeeks", career.cfg.phone["replyWeeks"])), "choice": -1}
	fresh.append(m["id"])


func _expire(career: CareerState) -> void:
	for id: String in status:
		var st: Dictionary = status[id]
		if st["state"] != OPEN or career.total_week < int(st["due"]):
			continue
		st["state"] = EXPIRED
		_add(st["room"], SYSTEM, "답장할 때를 놓쳤다", career.total_week, id, false)
		var m := _def(career, id)
		if m.has("ignore"):
			career.book.apply_effects(career, m["ignore"])


## 연락처에 넣는다 (이미 있으면 그대로)
func add_contact(id: String) -> void:
	if not contacts.has(id):
		contacts.append(id)


## 이야기 장면에서 주고받은 문자를 그 대화방 기록에 남긴다 (읽은 것으로). from: 인물 id 또는 ME
func add_story_line(room: String, from: String, text: String, tw: int, event_id: String) -> void:
	_add(room, from, text, tw, event_id)


func _add(room: String, from: String, text: String, tw: int, msg: String, bump: bool = true) -> void:
	if not rooms.has(room):
		rooms[room] = []
	(rooms[room] as Array).append({"from": from, "text": text, "tw": tw, "msg": msg})
	if bump or not order.has(room):
		order.erase(room)
		order.push_front(room)


# ---------- 답장 ----------

## 이 방에서 답장을 기다리는 문자 id ("" 없음)
func open_message(room: String) -> String:
	for id: String in status:
		if status[id]["state"] == OPEN and status[id]["room"] == room:
			return id
	return ""


## 이 방에서 지금 고를 수 있는 답장 글자들 (없으면 빈 배열)
func reply_options(career: CareerState, room: String) -> Array[String]:
	var out: Array[String] = []
	var id := open_message(room)
	if id == "":
		return out
	for r: Dictionary in _def(career, id)["replies"]:
		out.append(EventBook.fill(career, r["label"]))
	return out


## 남은 답장 기한 (주). 이번 주가 마지막이면 1, 답장을 기다리는 문자가 없으면 0
func weeks_left(career: CareerState, room: String) -> int:
	var id := open_message(room)
	return 0 if id == "" else maxi(1, int(status[id]["due"]) - career.total_week)


## 답장한다. 돌려주는 값: 새로 붙은 줄 수 (내 답장 + 돌아온 답 + 변화 안내 — 화면이 차례로 띄운다)
func reply(career: CareerState, room: String, index: int) -> int:
	var id := open_message(room)
	assert(id != "", "답장을 기다리는 문자가 없다")
	var r: Dictionary = _def(career, id)["replies"][index]
	var before: int = (rooms[room] as Array).size()
	var tw := career.total_week
	_add(room, ME, EventBook.fill(career, r.get("text", r["label"])), tw, id)
	for line: Dictionary in _lines(r.get("then", [])):
		_add(room, line.get("from", _def(career, id)["speaker"]), EventBook.fill(career, line["text"]), tw, id)
	# 관계 변화는 숫자로 보여 주지 않는다 (연락처의 상태 문구로만 드러난다). 컨디션·돈 같은 나머지 변화만 안내 줄로
	var fx: Dictionary = (r.get("effects", {}) as Dictionary).duplicate()
	if fx.has("rel"):
		career.book.apply_effects(career, {"rel": fx["rel"]})
		fx.erase("rel")
	var parts := career.book.apply_effects(career, fx)
	if not parts.is_empty():
		_add(room, SYSTEM, ", ".join(parts), tw, id)
	status[id]["state"] = REPLIED
	status[id]["choice"] = index
	return (rooms[room] as Array).size() - before


# ---------- 읽기 ----------

func lines(room: String) -> Array:
	return rooms.get(room, [])


func mark_read(room: String) -> void:
	unread.erase(room)


func total_unread() -> int:
	var n := 0
	for room: String in unread:
		n += int(unread[room])
	return n


## 허브 알림용: 아직 알리지 않은 새 문자들의 [{"room", "name": 방 이름, "text": 첫 줄}] — 부르면 비운다
func take_fresh(career: CareerState) -> Array:
	var out := []
	for id: String in fresh:
		var room: String = status[id]["room"]
		for line: Dictionary in lines(room):
			if line["msg"] == id:
				out.append({"room": room, "name": line_name(career, room, line), "text": line["text"]})
				break
	fresh.clear()
	return out


## 이 인물과 마지막으로 문자를 주고받은 주 (total_week, 없으면 -1)
func last_contact(person_id: String) -> int:
	var all: Array = lines(person_id)
	return -1 if all.is_empty() else int(all[-1]["tw"])


## 방 이름: 단체방은 content.json 의 이름, 인물 방은 부르는 이름
static func room_name(career: CareerState, room: String) -> String:
	if career.cfg.phone_rooms.has(room):
		return EventBook.fill(career, career.cfg.phone_rooms[room]["name"])
	var person := career.people.person(room)
	return person.call if person != null else room


static func is_group(career: CareerState, room: String) -> bool:
	return career.cfg.phone_rooms.has(room)


## 줄 앞에 붙일 보낸 사람 이름 (단체방에서만 쓴다)
static func line_name(career: CareerState, room: String, line: Dictionary) -> String:
	var person := career.people.person(line["from"])
	if is_group(career, room) and person != null:
		return "%s · %s" % [room_name(career, room), person.name]
	return room_name(career, room)


# ---------- 연락처의 관계 문구 ----------

## 마지막으로 연락처에서 본 뒤로 관계 문구가 바뀌었나. 바뀌었으면 예전 문구, 아니면 ""
func relation_was(career: CareerState, person_id: String) -> String:
	var now := People.relation_text(career.people.relation(person_id))
	var seen: String = seen_relation.get(person_id, now)
	return seen if seen != now else ""


## 연락처에서 이 사람을 봤다 (지금 문구를 기억한다)
func see_relation(career: CareerState, person_id: String) -> void:
	seen_relation[person_id] = People.relation_text(career.people.relation(person_id))


## 관계 문구가 바뀐 (만난) 인물 수 — 연락처 배지
func relation_changes(career: CareerState) -> int:
	var n := 0
	for id: String in career.people.by_id:
		if career.people.is_met(id) and relation_was(career, id) != "":
			n += 1
	return n


# ---------- 정의 읽기 ----------

static func room_of(m: Dictionary) -> String:
	return m.get("room", m["speaker"])


## 문자의 줄들 [{"from"?, "text"}]: lines(글자 또는 {from, text}) 또는 text 하나
static func lines_of(m: Dictionary) -> Array:
	return _lines(m["lines"]) if m.has("lines") else [{"text": m.get("text", "")}]


static func _lines(raw: Array) -> Array:
	var out := []
	for l in raw:
		out.append(l if l is Dictionary else {"text": str(l)})
	return out


## 이 문자에서 말하는 인물들 (보낸 사람 + 줄마다 적힌 from)
static func senders(m: Dictionary) -> Array:
	var out := [m["speaker"]]
	var all := lines_of(m)
	for r: Dictionary in m.get("replies", []):
		all.append_array(_lines(r.get("then", [])))
	for line: Dictionary in all:
		if line.has("from") and not out.has(line["from"]):
			out.append(line["from"])
	return out


func _def(career: CareerState, id: String) -> Dictionary:
	for m: Dictionary in career.cfg.messages:
		if m["id"] == id:
			return m
	# 저장에는 있는데 content.json 에서 빠진 문자: 답장 없는 빈 문자로 본다
	return {"id": id, "speaker": "", "replies": []}


# ---------- 무결성 검사 (테스트용) ----------

## 문자 정의 검사. 돌려주는 값: 문제 설명 목록 (비었으면 통과)
static func validate(cfg: CareerConfig) -> Array[String]:
	var errors: Array[String] = []
	var ids := {}
	for e: Dictionary in cfg.events:
		ids[e["id"]] = true
	var flags := EventBook.known_flags(cfg)
	var known_tokens := EventBook.token_names(cfg)
	for id: String in cfg.phone_rooms:
		if cfg.people.has(id):
			errors.append("단체방 %s: 인물 id 와 겹친다" % id)
		if str(cfg.phone_rooms[id].get("name", "")) == "":
			errors.append("단체방 %s: name 이 비었다" % id)
	for m: Dictionary in cfg.messages:
		var id: String = m.get("id", "")
		var where := "문자 %s" % id
		if id == "" or ids.has(id):
			errors.append("%s: id 가 없거나 이벤트·다른 문자와 겹친다" % where)
		ids[id] = true
		for k: String in m:
			if not k in KEYS:
				errors.append("%s: 모르는 키 %s" % [where, k])
		if not cfg.people.has(m.get("speaker", "")):
			errors.append("%s: speaker 는 인물 id" % where)
			continue
		var room := room_of(m)
		if room != m["speaker"] and not cfg.phone_rooms.has(room):
			errors.append("%s: room %s 없음 (인물 방은 room 을 적지 않는다)" % [where, room])
		if m.has("lines") == m.has("text"):
			errors.append("%s: lines 와 text 중 하나만" % where)
		for pid: String in senders(m):
			if not cfg.people.has(pid):
				errors.append("%s: from 인물 %s 없음" % [where, pid])
			elif pid != m["speaker"] and not cfg.phone_rooms.has(room):
				errors.append("%s: 인물 방에서는 그 사람만 말한다 (from %s)" % [where, pid])
		for k in ["grade", "week", "campWeek"]:
			if m.has(k) and ((m[k] as Array).size() != 2 or int(m[k][0]) > int(m[k][1])):
				errors.append("%s: %s 범위" % [where, k])
		for f: String in m.get("requires", []):
			if not flags.has(f):
				errors.append("%s: requires %s 를 세우는 선택지가 없다" % [where, f])
		for k in ["met", "forbidsMet"]:
			for pid: String in m.get(k, []):
				if not cfg.people.has(pid):
					errors.append("%s: %s 인물 %s 없음" % [where, k, pid])
		for k in ["minRel", "maxRel"]:
			for pid: String in m.get(k, {}):
				if not cfg.people.has(pid):
					errors.append("%s: %s 인물 %s 없음" % [where, k, pid])
		if (m.has("recentMinHits") or m.has("recentMaxHits")) != m.has("recentGames"):
			errors.append("%s: recentGames 는 recentMinHits·recentMaxHits 와 같이 쓴다" % where)
		if m.has("replyWeeks") and int(m["replyWeeks"]) <= 0:
			errors.append("%s: replyWeeks 는 1 이상" % where)
		var replies: Array = m.get("replies", [])
		if replies.size() == 1 or replies.size() > 3:
			errors.append("%s: 답장은 없거나 2~3개" % where)
		if m.has("ignore") and replies.is_empty():
			errors.append("%s: ignore 는 답장이 있는 문자에만" % where)
		var texts := []
		for line: Dictionary in lines_of(m):
			texts.append(line.get("text", ""))
		var effects := [m.get("ignore", {})]
		for r: Dictionary in replies:
			for k: String in r:
				if not k in ["label", "text", "effects", "then"]:
					errors.append("%s: 답장의 모르는 키 %s" % [where, k])
			texts.append(r.get("label", ""))
			if r.has("text"):
				texts.append(r["text"])
			for line: Dictionary in _lines(r.get("then", [])):
				texts.append(line.get("text", ""))
			effects.append(r.get("effects", {}))
		for t: String in texts:
			if t == "":
				errors.append("%s: 빈 글자" % where)
			for tok in EventBook._tokens(t):
				if not tok in known_tokens:
					errors.append("%s: 모르는 글자 치환 {%s}" % [where, tok])
		for fx: Dictionary in effects:
			for k: String in fx:
				if not k in EFFECTS:
					errors.append("%s: 문자에 못 쓰는 효과 %s" % [where, k])
			for pid: String in fx.get("rel", {}):
				if not cfg.people.has(pid):
					errors.append("%s: rel 인물 %s 없음" % [where, pid])
			for pid in EventBook._list(fx.get("meet", [])):
				if not cfg.people.has(pid):
					errors.append("%s: meet 인물 %s 없음" % [where, pid])
			if fx.has("schedule"):
				var target: String = fx["schedule"].get("event", "")
				if not (EventBook._has_event(cfg, target) or target in cfg.pending_events) or int(fx["schedule"].get("weeks", 0)) <= 0:
					errors.append("%s: schedule 대상 %s" % [where, target])
	return errors
