class_name SaveGame
extends RefCounted
## 커리어 저장·불러오기.
##
## 커리어 객체를 통째로 훑어 저장한다: 스크립트 변수를 하나씩 읽어 객체는 {"$t": 종류, "$id": 번호, "f": {필드}} 로,
## 같은 객체를 두 번째로 만나면 {"$ref": 번호} 로 적는다. 그래서 "라이벌 = 라이벌 학교 명단의 그 타자"처럼
## 두 곳이 같은 객체를 가리키는 관계가 불러온 뒤에도 그대로 살아 있다.
## 수치·콘텐츠(CareerConfig·BattingConfig)는 저장하지 않고 불러올 때 지금 설정을 다시 끼운다.
## 이벤트 정의(EventBook.defs·order)도 content.json 에서 다시 만든다.
##
## 파일은 Godot 이진 형식(var_to_bytes)이다. 글자 형식(var_to_str)은 실수를 마지막 자리에서 반올림하고
## 사전 키를 정렬해 버려서, 불러온 뒤 난수 결과가 원래와 달라진다 (원칙 2: 같은 입력 → 같은 결과).
## 이진 형식은 실수·int 키·사전 순서를 그대로 지키고, 객체는 풀지 않는다 (저장 파일로 코드가 실행되지 않는다).

const VERSION := 1
const PATH := "user://career.save"

## 저장하는 객체 종류 (이름 -> 스크립트). 새 객체 종류를 커리어에 넣으면 여기에 더한다 (빠지면 저장 테스트가 실패한다)
static var TYPES := {
	"CareerState": CareerState,
	"PlayerData": PlayerData,
	"SeasonStats": PlayerData.SeasonStats,
	"School": School,
	"Team": Team,
	"Batter": Team.Batter,
	"Pitcher": Team.Pitcher,
	"BatterSkills": BatterSkills,
	"People": People,
	"Person": People.Person,
	"Rival": Rival,
	"Scouting": Scouting,
	"EventBook": EventBook,
	"CareerRecords": CareerRecords,
	"GameEntry": CareerRecords.GameEntry,
	"PhaseEntry": CareerRecords.PhaseEntry,
}

## 저장하지 않는 필드 (종류 -> 필드). 설정에서 다시 만든다
const SKIP := {
	"CareerState": ["cfg", "batting"],
	"EventBook": ["defs", "order"],
}


# ---------- 파일 ----------

static func exists(path: String = PATH) -> bool:
	return FileAccess.file_exists(path)


## 저장. 돌려주는 값: "" 성공, 아니면 이유. 임시 파일에 다 쓴 뒤 바꿔 넣어서, 쓰다가 꺼져도 앞 저장은 남는다
static func save(career: CareerState, path: String = PATH) -> String:
	var bytes := to_bytes(career)
	if bytes.is_empty():
		return "저장할 수 없는 값이 있다"
	var tmp := path + ".tmp"
	var f := FileAccess.open(tmp, FileAccess.WRITE)
	if f == null:
		return "파일을 열 수 없다 (%s)" % error_string(FileAccess.get_open_error())
	f.store_buffer(bytes)
	f.close()
	var err := DirAccess.rename_absolute(tmp, path)
	return "" if err == OK else "저장 파일을 바꿔 넣지 못했다 (%s)" % error_string(err)


## 불러오기. 돌려주는 값: {"career": CareerState 또는 null, "error": "" 또는 이유}
static func load_career(cfg: CareerConfig, batting: BattingConfig, path: String = PATH) -> Dictionary:
	if not exists(path):
		return {"career": null, "error": "저장이 없다"}
	return from_bytes(FileAccess.get_file_as_bytes(path), cfg, batting)


static func delete(path: String = PATH) -> void:
	if exists(path):
		DirAccess.remove_absolute(path)


## 로비·선수 만들기 화면에 보여 줄 한 줄 ("김도윤 · 2학년 · 주말리그 후반기 3/6")
static func summary(career: CareerState) -> String:
	if career.is_season_over():
		return "%s · %d학년 · 시즌 결산" % [career.player.name, career.player.grade]
	return "%s · %d학년 · %s %s" % [career.player.name, career.player.grade, career.phase_name(), career.round_text()]


# ---------- 바이트 ----------

static func to_bytes(career: CareerState) -> PackedByteArray:
	var memo := {}
	var root = _encode(career, memo)
	if root == null:
		return PackedByteArray()
	return var_to_bytes({"version": VERSION, "career": root})


static func from_bytes(bytes: PackedByteArray, cfg: CareerConfig, batting: BattingConfig) -> Dictionary:
	var broken := {"career": null, "error": "저장 파일이 깨졌다"}
	if bytes.size() < 4:
		return broken
	var data = bytes_to_var(bytes)
	if typeof(data) != TYPE_DICTIONARY or not data.has("career") or typeof(data["career"]) != TYPE_DICTIONARY:
		return broken
	if int(data.get("version", 0)) != VERSION:
		return {"career": null, "error": "저장 버전이 다르다 (%s)" % data.get("version")}
	var root: Dictionary = data["career"]
	if root.get("$t", "") != "CareerState":
		return broken
	# 커리어는 지금 설정으로 새로 만든 뒤 저장된 값으로 덮어쓴다 (설정 참조·이벤트 정의는 새 것이 남는다).
	# 먼저 저장에 든 객체를 모두 만들어 두고 나서 필드를 채운다 — 어느 순서로 만나든 $ref 가 가리킬 객체가 있다
	var career := CareerState.new(cfg, String(root["f"]["player"]["f"].get("name", "")), batting)
	var memo := {int(root["$id"]): career}
	_create_all(root["f"], memo, cfg)
	var filled := {int(root["$id"]): true}
	_fill(career, "CareerState", root["f"], memo, filled, cfg)
	return {"career": career, "error": ""}


# ---------- 객체 <-> 사전 ----------

static func _type_of(obj: Object) -> String:
	var script: Script = obj.get_script()
	for name: String in TYPES:
		if TYPES[name] == script:
			return name
	return ""


## 값 → 저장용 값. 저장할 수 없는 객체를 만나면 오류를 내고 null
static func _encode(v: Variant, memo: Dictionary) -> Variant:
	match typeof(v):
		TYPE_OBJECT:
			var obj: Object = v
			if obj == null:
				return null
			var key := obj.get_instance_id()
			if memo.has(key):
				return {"$ref": memo[key]}
			var type := _type_of(obj)
			if type == "":
				push_error("저장할 수 없는 객체: %s (SaveGame.TYPES 에 더해야 한다)" % obj)
				return null
			var id := memo.size()
			memo[key] = id
			var fields := {}
			var skip: Array = SKIP.get(type, [])
			for prop: Dictionary in obj.get_property_list():
				if not (prop["usage"] & PROPERTY_USAGE_SCRIPT_VARIABLE) or prop["name"] in skip:
					continue
				fields[prop["name"]] = _encode(obj.get(prop["name"]), memo)
			return {"$t": type, "$id": id, "f": fields}
		TYPE_ARRAY:
			var out := []
			for x in v:
				out.append(_encode(x, memo))
			return out
		TYPE_DICTIONARY:
			var out := {}
			for k in v:
				out[k] = _encode(v[k], memo)
			return out
		TYPE_CALLABLE, TYPE_SIGNAL, TYPE_RID:
			push_error("저장할 수 없는 값: %s" % v)
			return null
	return v


## 첫 단계: 저장에 든 객체를 모두 만들어 번호로 적어 둔다 (필드는 아직 비어 있다)
static func _create_all(v: Variant, memo: Dictionary, cfg: CareerConfig) -> void:
	match typeof(v):
		TYPE_ARRAY:
			for x in v:
				_create_all(x, memo, cfg)
		TYPE_DICTIONARY:
			var d: Dictionary = v
			if d.has("$t"):
				var type: String = d["$t"]
				memo[int(d["$id"])] = EventBook.new(cfg) if type == "EventBook" else TYPES[type].new()
				_create_all(d["f"], memo, cfg)
			else:
				for k in d:
					_create_all(d[k], memo, cfg)


## 둘째 단계: 저장용 값 → 값. 객체는 만들어 둔 것을 쓰고, 처음 만났을 때 필드를 채운다
static func _decode(v: Variant, memo: Dictionary, filled: Dictionary, cfg: CareerConfig) -> Variant:
	match typeof(v):
		TYPE_ARRAY:
			var out := []
			for x in v:
				out.append(_decode(x, memo, filled, cfg))
			return out
		TYPE_DICTIONARY:
			var d: Dictionary = v
			if d.has("$ref"):
				return memo[int(d["$ref"])]
			if d.has("$t"):
				var id := int(d["$id"])
				var obj: Object = memo[id]
				if not filled.has(id):
					filled[id] = true
					_fill(obj, d["$t"], d["f"], memo, filled, cfg)
				return obj
			var out := {}
			for k in d:
				out[k] = _decode(d[k], memo, filled, cfg)
			return out
	return v


## 저장된 필드를 객체에 채운다. 타입이 붙은 배열(Array[int] 등)은 assign 으로 넣어 타입을 지킨다.
## 저장에는 있는데 지금 코드에 없는 필드는 버리고, 코드에만 있는 새 필드는 기본값으로 둔다
static func _fill(obj: Object, type: String, fields: Dictionary, memo: Dictionary, filled: Dictionary, cfg: CareerConfig) -> void:
	var known := {}
	for prop: Dictionary in obj.get_property_list():
		if prop["usage"] & PROPERTY_USAGE_SCRIPT_VARIABLE:
			known[prop["name"]] = true
	var skip: Array = SKIP.get(type, [])
	for name: String in fields:
		if not known.has(name) or name in skip:
			continue
		var value = _decode(fields[name], memo, filled, cfg)
		var cur = obj.get(name)
		if typeof(cur) == TYPE_ARRAY and (cur as Array).is_typed() and typeof(value) == TYPE_ARRAY:
			(cur as Array).assign(value)
		else:
			obj.set(name, value)
