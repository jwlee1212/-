class_name CutsceneView
extends Control
## 컷신 화면 (애니메이션 영화풍 도트 장면, 2026-11-05). 무엇을 그릴지는 CutscenePlan 이 시간만으로 정하고, 여기서는 그린다:
## 판(배경)을 카메라 자리만큼 잘라 4배로, 그 위에 인물 칸, 위아래 검은 띠(아래 띠에 자막), 번쩍임·암전.
## 도트는 정수 배율·같은 격자로만 (카메라·인물 자리는 그림 픽셀 단위로 반올림한다 — 원칙 6). 자막·버튼은 매끈한 글자.
## 시간은 Time.get_ticks_usec 하나로 잰다 (프레임 수를 세지 않는다 — 원칙 4). 다 보면 finished.
## 소리는 앱의 SoundPlayer 를 부모 쪽에서 찾아 쓴다 (없으면 조용히 — 헤드리스 테스트·스크린샷).
##
## 정지 그림 컷 ("still", 2026-11-05 유저: 외부 그림을 98칸으로 줄이니 화질이 깨진다): 원본 해상도 그림을 화면 가운데 TV 틀에
## 띄운다 — 둘레는 검게, 그림 가장자리는 검은색으로 부드럽게 녹이고, 자막은 틀 아래. 화면은 기기 해상도로 그려지므로
## (stretch canvas_items) 그림이 기기 픽셀만큼 또렷하다. 이 그림만 부드러운 필터(LINEAR)로 그린다 — 도트 판·인물은 그대로 정수 배율.
## 틀 크기·비율·가장자리 두께는 balance.json career.cutscene (tvWidth · tvMaxHeight · tvAspect · tvEdgePx).

signal finished

const BG_PATH := "res://art/pixel/cutscene_bg.png"
const SHEET_PATH := "res://art/pixel/cutscene.png"
## 정지 그림 (scripts/import_cutscene_stills.py 가 만든다)
const STILL_DIR := "res://art/cutscene_stills/%s.png"

var _cs: Dictionary
var _knobs: Dictionary
var _bg: Texture2D
var _sheet: Texture2D
var _start_us := 0
var _last_t := 0.0
var _done := false
var _sound: SoundPlayer
var _caption: Label
var _who: Label
var _skip: Button
## 정지 그림 층 (부드러운 필터) · 그 위 덮개 (가장자리 녹이기·번쩍임·암전) · 불러 둔 정지 그림 (이름 → 그림)
var _still_layer: Control
var _cover: Control
var _stills := {}
## 지금 그린 장면 (테스트·스크린샷용)
var current := {}


func _init(career: CareerState, id: String) -> void:
	_cs = career.cfg.cutscenes[id]
	_knobs = career.cfg.cutscene


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	clip_contents = true
	_bg = load(BG_PATH)
	_sheet = load(SHEET_PATH)
	_sound = _find_sound()
	_still_layer = Control.new()
	_still_layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_still_layer.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	add_child(UiKit.fill_parent(_still_layer))
	_still_layer.draw.connect(_draw_still)
	_cover = Control.new()
	_cover.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(_cover))
	_cover.draw.connect(_draw_cover)
	for shot: Dictionary in _cs["shots"]:
		if shot.has("still") and not _stills.has(shot["still"]) and ResourceLoader.exists(STILL_DIR % shot["still"]):
			_stills[shot["still"]] = load(STILL_DIR % shot["still"])
	_who = UiKit.label("", Tokens.FONT_CAPTION, Tokens.CUTSCENE_WHO, Tokens.FONT_BOLD)
	_caption = UiKit.label("", Tokens.FONT_BODY + 1, Tokens.CUTSCENE_TEXT, Tokens.FONT_BOLD)
	for l: Label in [_who, _caption]:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(l)
	_skip = UiKit.button("건너뛰기  ▶▶", finish, Tokens.CUTSCENE_BAR, Tokens.FONT_CAPTION)
	_skip.add_theme_color_override("font_color", Tokens.CUTSCENE_TEXT)
	_skip.modulate.a = 0.7
	add_child(_skip)
	_skip.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_XS)
	_skip.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_skip.visible = false
	_start_us = Time.get_ticks_usec()
	show_at(0.0)


func _process(_delta: float) -> void:
	if _done:
		return
	var t := float(Time.get_ticks_usec() - _start_us) / 1e6 * float(_knobs["speed"])
	show_at(t)


## 시각 t(초)의 장면을 그린다 (스크린샷 스크립트가 원하는 순간을 찍을 때도 쓴다). 지나간 소리를 울리고, 끝이면 finished
func show_at(t: float) -> void:
	for cue: Dictionary in CutscenePlan.cues(_cs, _last_t, t):
		if _sound != null:
			_sound.play(SoundSynth.Sfx[cue["sfx"]], cue["volume"], cue["pitch"])
	_last_t = t
	current = CutscenePlan.frame(_cs, t)
	_skip.visible = t >= float(_knobs["skipAfterSec"])
	_place_caption(t)
	queue_redraw()
	_still_layer.queue_redraw()
	_cover.queue_redraw()
	if t >= CutscenePlan.duration(_cs):
		finish()


## 끝 (다 봤거나 [건너뛰기])
func finish() -> void:
	if _done:
		return
	_done = true
	finished.emit()


## 그림 픽셀 1칸의 화면 크기 · 그림(VIEW)이 놓이는 왼쪽 위 (가운데 정렬, 남는 곳은 검은 띠 색)
func _scale() -> float:
	return float(CutsceneMeta.SCALE)


func _origin() -> Vector2:
	var view := CutsceneMeta.VIEW * _scale()
	return ((size - view) / 2.0).floor()


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Tokens.CUTSCENE_BAR)
	if current.is_empty() or current.has("still"):
		return
	var s := _scale()
	var o := _origin()
	var view := CutsceneMeta.VIEW
	var panel: Array = CutsceneMeta.PANELS[current["panel"]]
	var cam: Vector2i = current["cam"]
	# 판: 카메라가 판 밖으로 나가지 않게
	var cx := clampi(cam.x, 0, maxi(0, int(panel[1]) - int(view.x)))
	var cy := clampi(cam.y, 0, CutsceneMeta.PANEL_H - int(view.y))
	draw_texture_rect_region(_bg, Rect2(o, view * s), Rect2(cx, int(panel[0]) * CutsceneMeta.PANEL_H + cy, view.x, view.y))
	# 인물 칸: 발 자리를 판 위 자리에 맞춘다 (보이는 곳만 잘라 그린다)
	for a: Dictionary in current["actors"]:
		var row: Array = CutsceneMeta.ROWS[a["row"]]
		var top_left: Vector2 = Vector2(a["pos"]) - CutsceneMeta.FOOT - Vector2(cx, cy)
		var src := Rect2(Vector2(int(a["frame"]) * CutsceneMeta.CELL.x, int(row[0]) * CutsceneMeta.CELL.y), CutsceneMeta.CELL)
		var vis := Rect2(top_left, CutsceneMeta.CELL).intersection(Rect2(Vector2.ZERO, view))
		if vis.size.x <= 0 or vis.size.y <= 0:
			continue
		var cut := Rect2(src.position + (vis.position - top_left), vis.size)
		draw_texture_rect_region(_sheet, Rect2(o + vis.position * s, vis.size * s), cut)
	# 번쩍임 · 암전 (그림 위)
	if current["flash"] > 0.0:
		draw_rect(Rect2(o, view * s), Color(Tokens.CUTSCENE_FLASH, float(current["flash"]) * 0.9))
	if current["fade"] > 0.0:
		draw_rect(Rect2(o, view * s), Color(Tokens.CUTSCENE_FADE, float(current["fade"])))
	# 위아래 검은 띠 (영화관 비율) — 아래 띠에 자막
	var bar := float(_knobs["bar"]) * s
	draw_rect(Rect2(Vector2(0, o.y - 1), Vector2(size.x, bar + 1)), Tokens.CUTSCENE_BAR)
	draw_rect(Rect2(Vector2(0, o.y + view.y * s - bar), Vector2(size.x, bar + 1)), Tokens.CUTSCENE_BAR)


# ---------- 정지 그림 컷 (TV 틀) ----------

## TV 틀 자리: 가로는 화면의 tvWidth, 세로가 tvMaxHeight 를 넘으면 줄인다. 아래에 자막 두 줄 자리를 남기고 가운데
func tv_rect() -> Rect2:
	var aspect := float(_knobs["tvAspect"])
	var w := minf(size.x * float(_knobs["tvWidth"]), size.y * float(_knobs["tvMaxHeight"]) * aspect)
	var h := w / aspect
	var caption_h := float(Tokens.FONT_BODY + Tokens.FONT_CAPTION + Tokens.SPACE_MD)
	var y := (size.y - h - caption_h) / 2.0
	return Rect2(Vector2((size.x - w) / 2.0, y).floor(), Vector2(w, h).floor())


## 그림에서 지금 보는 곳: 틀 비율로 가장 크게 자른 창을 zoom 만큼 좁히고, pan(0~1)으로 남는 곳 중 자리를 고른다
func _still_window(tex: Texture2D) -> Rect2:
	var src := tex.get_size()
	var aspect := float(_knobs["tvAspect"])
	var win := Vector2(src.y * aspect, src.y) if src.x / src.y > aspect else Vector2(src.x, src.x / aspect)
	win /= float(current["zoom"])
	var pan: Vector2 = current["pan"]
	return Rect2((src - win) * pan, win)


func _draw_still() -> void:
	if not current.has("still") or not _stills.has(current["still"]):
		return
	var tex: Texture2D = _stills[current["still"]]
	var tv := tv_rect()
	_still_layer.draw_texture_rect_region(tex, Rect2(tv.position + current["shake"], tv.size), _still_window(tex))


## 덮개: 정지 그림 컷이면 틀 바깥을 검게(흔들림이 삐져나온 곳까지) + 가장자리를 검은색으로 녹이고, 그 위에 번쩍임·암전
func _draw_cover() -> void:
	if not current.has("still"):
		return
	var tv := tv_rect()
	var full := Rect2(Vector2.ZERO, size)
	for r: Rect2 in [Rect2(0, 0, size.x, tv.position.y), Rect2(0, tv.end.y, size.x, size.y - tv.end.y),
			Rect2(0, tv.position.y, tv.position.x, tv.size.y), Rect2(tv.end.x, tv.position.y, size.x - tv.end.x, tv.size.y)]:
		_cover.draw_rect(r.intersection(full), Tokens.CUTSCENE_BAR)
	var edge := int(_knobs["tvEdgePx"])
	for i in edge:
		var k := 1.0 - float(i) / float(edge)
		_cover.draw_rect(tv.grow(-float(i)), Color(Tokens.CUTSCENE_BAR, k * k), false, 1.0)
	if current["flash"] > 0.0:
		_cover.draw_rect(tv, Color(Tokens.CUTSCENE_FLASH, float(current["flash"]) * 0.9))
	if current["fade"] > 0.0:
		_cover.draw_rect(tv, Color(Tokens.CUTSCENE_FADE, float(current["fade"])))


## 자막: 도트 판 컷은 아래 검은 띠 가운데(화자 이름은 그 위), 정지 그림 컷은 TV 틀 아래(화자 이름 → 대사). 나타나고 사라질 때 투명도로
func _place_caption(t: float) -> void:
	var caps: Array = current.get("captions", [])
	if caps.is_empty():
		_caption.text = ""
		_who.text = ""
		return
	var cap: Dictionary = caps[-1]
	_caption.text = cap["text"]
	_who.text = cap["who"]
	_caption.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	if current.has("still"):
		var tv := tv_rect()
		_who.size = Vector2(size.x, Tokens.FONT_CAPTION + Tokens.SPACE_XS)
		_who.position = Vector2(0, tv.end.y + Tokens.SPACE_XS)
		_caption.size = Vector2(size.x, Tokens.FONT_BODY + Tokens.SPACE_SM)
		_caption.position = Vector2(0, _who.position.y + (_who.size.y if cap["who"] != "" else 0.0))
	else:
		var bar := float(_knobs["bar"]) * _scale()
		var bottom := _origin().y + CutsceneMeta.VIEW.y * _scale()
		_caption.size = Vector2(size.x, bar)
		_caption.position = Vector2(0, bottom - bar)
		_who.size = Vector2(size.x, Tokens.FONT_CAPTION + Tokens.SPACE_XS)
		_who.position = Vector2(0, bottom - bar - _who.size.y - 2)
	_caption.modulate.a = _caption_alpha(t)
	_who.modulate.a = _caption.modulate.a


func _caption_alpha(t: float) -> float:
	var at := CutscenePlan.locate(_cs, t)
	var lt: float = at[1]
	var fade := maxf(float(_knobs["captionFadeSec"]), 0.001)
	for c: Dictionary in _cs["shots"][at[0]].get("captions", []):
		if lt >= float(c["from"]) and lt < float(c["to"]):
			return clampf(minf(lt - float(c["from"]), float(c["to"]) - lt) / fade, 0.0, 1.0)
	return 0.0


## 이 컷신이 쓰는 정지 그림 중 파일이 있는 것 (검증용 — 없는 그림은 CutscenePlan.validate 가 걸러 컷신을 건너뛰게 한다)
static func still_names(cs: Dictionary) -> Array:
	var out := []
	for shot: Dictionary in cs.get("shots", []):
		if shot.has("still") and ResourceLoader.exists(STILL_DIR % shot["still"]):
			out.append(shot["still"])
	return out


func _find_sound() -> SoundPlayer:
	var n := get_parent()
	while n != null:
		if "sound" in n and n.get("sound") is SoundPlayer:
			return n.get("sound")
		n = n.get_parent()
	return null
