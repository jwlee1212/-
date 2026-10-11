class_name DialogueRun
extends RefCounted
## 이벤트 하나의 대화 진행 (로직만 — 화면은 DialogueView). 대본(EventBook.script_of)을 한 걸음씩 읽는다.
##
## next() 가 돌려주는 걸음:
##   {"type": "line", "who": 인물 id | "player" | "" (지문) | "?" (인물이 아닌 화자), "name", "role", "portrait", "text"}
##   {"type": "choice", "options": [{"label", "cost", "block"}]}   → choose(번호)를 불러야 다음으로 간다
##   {"type": "end"}                                               → commit() 으로 효과를 반영한다
## 선택하면 그 선택지의 then(뒤따르는 대사)이 이어지고, 다 끝나면 선택 지점 다음 줄로 돌아온다.
## 효과·돈은 고르는 순간이 아니라 commit() 에서 한꺼번에 반영한다 — 대화 도중에 꺼도 반쯤 적용된 상태가 남지 않는다.
## 확률로 갈리는 선택지(outcomes)는 커리어 시드로 뽑는다 (같은 주에 다시 해도 같은 갈래, 원칙 2).

var career: CareerState
var event: Dictionary
## 고른 선택지 글자 (순서대로), 고른 선택지들의 효과, 쓴 돈
var labels: Array[String] = []
var effects: Array = []
var cost := 0
## 마지막으로 나온 대사·지문 (기록에 "결과"로 남는다)
var last_text := ""

var _queue: Array = []
## 지금 골라야 하는 선택지들 (없으면 빈 배열), 지금까지 고른 횟수
var _options: Array = []
var _picked := 0


func _init(p_career: CareerState, p_event: Dictionary) -> void:
	career = p_career
	event = p_event
	_queue = EventBook.script_of(p_event).duplicate()


func next() -> Dictionary:
	if not _options.is_empty():
		return _choice_step()
	if _queue.is_empty():
		return {"type": "end"}
	var node: Dictionary = _queue.pop_front()
	if node.has("choices"):
		_options = node["choices"]
		return _choice_step()
	return _line_step(node)


func at_choice() -> bool:
	return not _options.is_empty()


## 이 선택지를 못 고르는 이유 ("" 고를 수 있음). 이번 대화에서 이미 쓴 돈을 뺀 지갑으로 본다
func block(index: int) -> String:
	var need := int(_options[index].get("cost", 0))
	if career.player.money - cost < need:
		return "돈이 모자라다 (%s 필요)" % PlayerData.money_text(need)
	return ""


func choose(index: int) -> void:
	assert(at_choice() and block(index) == "", "고를 수 없는 선택지")
	var c: Dictionary = _options[index]
	labels.append(EventBook.fill(career, c["label"]))
	cost += int(c.get("cost", 0))
	if c.has("effects"):
		effects.append(c["effects"])
	var branch := c
	if c.has("outcomes"):
		var salt := "choice:%s" % event["id"] if _picked == 0 else "choice:%s:%d" % [event["id"], _picked]
		branch = EventBook.roll_outcome(c["outcomes"], career._rng(salt))
		if branch.has("effects"):
			effects.append(branch["effects"])
	_queue = (branch.get("then", []) as Array) + _queue
	_options = []
	_picked += 1


## 고를 수 있는 선택지 번호: want 번째(없으면 마지막), 막혀 있으면 처음으로 고를 수 있는 것
func pick(want: int) -> int:
	var i := clampi(want, 0, _options.size() - 1)
	if block(i) == "":
		return i
	for k in _options.size():
		if block(k) == "":
			return k
	assert(false, "%s: 고를 수 있는 선택지가 없다" % event["id"])
	return 0


## 끝까지 자동으로 (선택 지점마다 want 번째). 화면 없이 진행하는 테스트·빨리 감기·건너뛰기용
func auto(want: int) -> void:
	while true:
		var step := next()
		match step["type"]:
			"end":
				return
			"choice":
				choose(pick(want))


## 효과를 반영하고 이벤트를 끝낸다. 돌려주는 값: 결과 문장 (+ 변화 설명)
func commit() -> String:
	return career.book.commit(self)


func _choice_step() -> Dictionary:
	var out := []
	for i in _options.size():
		var c: Dictionary = _options[i]
		out.append({"label": EventBook.fill(career, c["label"]), "cost": int(c.get("cost", 0)), "block": block(i)})
	return {"type": "choice", "options": out}


func _line_step(node: Dictionary) -> Dictionary:
	var speaker: String = node.get("speaker", "")
	var text := EventBook.fill(career, node["text"])
	# 대사 맨 앞 괄호는 지문으로 따로: "(모자를 벗는다) 고생했다." → 지문 한 줄 + 대사 한 줄
	if speaker != "" and text.begins_with("("):
		var close := text.find(")")
		if close > 0:
			var rest := text.substr(close + 1).strip_edges()
			if rest != "":
				_queue.push_front({"speaker": speaker, "text": rest, "portrait": node.get("portrait", "")})
			speaker = ""
			text = text.substr(1, close - 1).strip_edges()
	elif speaker == "" and text.begins_with("(") and text.ends_with(")"):
		text = text.substr(1, text.length() - 2).strip_edges()
	last_text = text
	var step := {"type": "line", "who": "", "name": "", "role": "", "portrait": "", "text": text}
	if speaker == "":
		return step
	if speaker == "player":
		step["who"] = "player"
		step["name"] = career.player.name
		return step
	var person := career.people.person(speaker)
	if person != null:
		step["who"] = person.id
		step["name"] = person.call
		step["role"] = person.role
		step["portrait"] = person.id
	else:
		step["who"] = "?"
		step["name"] = speaker
		# 인물이 아닌 화자: 줄에 적힌 일러스트, 없으면 이벤트의 화자일 때 이벤트 일러스트
		var p: String = node.get("portrait", "")
		step["portrait"] = p if p != "" else (event.get("portrait", "none") if speaker == event["speaker"] else "none")
	return step
