class_name CutsceneView
extends Control
## 컷신 화면 (애니메이션 영화풍 도트 장면, 2026-11-05). 무엇을 그릴지는 CutscenePlan 이 시간만으로 정하고, 여기서는 그린다:
## 판(배경)을 카메라 자리만큼 잘라 4배로, 그 위에 인물 칸, 위아래 검은 띠(아래 띠에 자막), 번쩍임·암전.
## 도트는 정수 배율·같은 격자로만 (카메라·인물 자리는 그림 픽셀 단위로 반올림한다 — 원칙 6). 자막·버튼은 매끈한 글자.
## 시간은 Time.get_ticks_usec 하나로 잰다 (프레임 수를 세지 않는다 — 원칙 4). 다 보면 finished.
## 소리는 앱의 SoundPlayer 를 부모 쪽에서 찾아 쓴다 (없으면 조용히 — 헤드리스 테스트·스크린샷).

signal finished

const BG_PATH := "res://art/pixel/cutscene_bg.png"
const SHEET_PATH := "res://art/pixel/cutscene.png"

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
	if current.is_empty():
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


## 자막: 아래 검은 띠 가운데, 화자 이름은 그 위 그림 가장자리에. 나타나고 사라질 때 투명도로
func _place_caption(t: float) -> void:
	var caps: Array = current.get("captions", [])
	var bar := float(_knobs["bar"]) * _scale()
	var o := _origin()
	var bottom := o.y + CutsceneMeta.VIEW.y * _scale()
	if caps.is_empty():
		_caption.text = ""
		_who.text = ""
		return
	var cap: Dictionary = caps[-1]
	_caption.text = cap["text"]
	_who.text = cap["who"]
	_caption.size = Vector2(size.x, bar)
	_caption.position = Vector2(0, bottom - bar)
	_caption.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
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


func _find_sound() -> SoundPlayer:
	var n := get_parent()
	while n != null:
		if "sound" in n and n.get("sound") is SoundPlayer:
			return n.get("sound")
		n = n.get_parent()
	return null
