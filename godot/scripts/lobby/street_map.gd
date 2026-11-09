class_name StreetMap
extends RefCounted
## 로비 골목의 장소 배치 (로직 — 그리기·입력 없음). 정의는 content.json street.<구간>.
## 좌표는 모두 골목 그림 픽셀 기준이다. 화면은 이 값에 (화면 높이 / 그림 높이) 를 곱해 그린다.
## 장소마다 누르는 자리(x ~ x+w)와 내 선수가 멈추는 문(door), 들어가는 메뉴(menu)가 있다.

## 장소가 열 수 있는 메뉴
const MENUS := ["game", "training", "player", "records", "team", "shop", "diner"]
## 그림이 없을 때 코드로 임시로 그리는 건물 모양
const KINDS := ["apartment", "store", "container", "field", "school", "stadium"]

var art := ""
var width := 1.0
var height := 1.0
var ground_y := 0.0
## 처음 로비를 열 때 내 선수가 서 있는 장소
var start := ""
## [{id, name, menu, x, w, door, kind}] — 왼쪽부터
var places: Array = []


static func from(def: Dictionary) -> StreetMap:
	var m := StreetMap.new()
	m.art = str(def.get("art", ""))
	m.width = float(def["width"])
	m.height = float(def["height"])
	m.ground_y = float(def["groundY"])
	m.start = str(def.get("start", ""))
	for p: Dictionary in def["places"]:
		m.places.append({
			"id": str(p["id"]), "name": str(p["name"]), "menu": str(p["menu"]),
			"x": float(p["x"]), "w": float(p["w"]), "door": float(p["door"]), "kind": str(p.get("kind", "store")),
		})
	return m


## id 의 장소 (없으면 빈 사전)
func place(id: String) -> Dictionary:
	for p: Dictionary in places:
		if p["id"] == id:
			return p
	return {}


## 그림 x 좌표에 있는 장소 id (빈 땅이면 "")
func place_at(x: float) -> String:
	for p: Dictionary in places:
		if x >= p["x"] and x < p["x"] + p["w"]:
			return p["id"]
	return ""


## 장소의 문 자리 (없는 장소면 시작 장소의 문, 그것도 없으면 그림 가운데)
func door_x(id: String) -> float:
	var p := place(id)
	if p.is_empty():
		p = place(start)
	return p["door"] if not p.is_empty() else width / 2.0


## focus 를 화면 가운데 두는 카메라 왼쪽 끝 (그림 밖이 보이지 않게 자른다). view_w = 화면 너비(그림 픽셀)
func camera_left(focus: float, view_w: float) -> float:
	return clamp_camera(focus - view_w / 2.0, view_w)


func clamp_camera(left: float, view_w: float) -> float:
	return clampf(left, 0.0, maxf(0.0, width - view_w))


## 정의 검사: 빠진 값, 모르는 메뉴·모양, 겹치는 자리, 그림 밖, 자리 밖의 문, 시작 장소
static func validate(def: Dictionary) -> PackedStringArray:
	var errs := PackedStringArray()
	for k: String in ["width", "height", "groundY", "places"]:
		if not def.has(k):
			errs.append("street: %s 가 없다" % k)
	if not errs.is_empty():
		return errs
	var w := float(def["width"])
	var ids := {}
	var last_end := -INF
	for p: Dictionary in def["places"]:
		var id := str(p.get("id", ""))
		var missing := false
		for k: String in ["id", "name", "menu", "x", "w", "door"]:
			if not p.has(k):
				errs.append("street: 장소 %s 에 %s 가 없다" % [id, k])
				missing = true
		if missing:
			continue
		if ids.has(id):
			errs.append("street: 장소 id %s 가 겹친다" % id)
		ids[id] = true
		if not str(p.get("menu", "")) in MENUS:
			errs.append("street: %s 의 메뉴 %s 를 모른다" % [id, p.get("menu", "")])
		if p.has("kind") and not str(p["kind"]) in KINDS:
			errs.append("street: %s 의 모양 %s 를 모른다" % [id, p["kind"]])
		var x := float(p.get("x", 0))
		var pw := float(p.get("w", 0))
		var door := float(p.get("door", 0))
		if pw <= 0.0 or x < 0.0 or x + pw > w:
			errs.append("street: %s 의 자리가 그림 밖이다" % id)
		if x < last_end:
			errs.append("street: %s 가 왼쪽 장소와 겹치거나 순서가 틀렸다" % id)
		last_end = x + pw
		if door < x or door > x + pw:
			errs.append("street: %s 의 문이 자리 밖이다" % id)
	if def.has("start") and not ids.has(str(def["start"])):
		errs.append("street: 시작 장소 %s 가 없다" % def["start"])
	return errs
