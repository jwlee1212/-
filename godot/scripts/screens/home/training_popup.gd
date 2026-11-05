class_name TrainingPopup
extends Control
## 스케줄 칸 하나의 훈련 장면 창 (2026-11-01): 주위를 어둡게 덮고 가운데 창에 도트 훈련 애니메이션을 튼 뒤 결과를 보여 준다.
##   장면 → (trainingAnimSec 초) → 결과 한 줄 (trainingResultSec 초) → finished
## 창을 누르면 장면 중이면 결과로, 결과 중이면 바로 닫는다. [모두 건너뛰기]는 skip_all (남은 칸을 다 넘기는 건 홈이 한다).
## 그림은 art/pixel/training.png (scripts/make_training_art.py, 줄·칸은 TrainingMeta). 시간은 balance.json career.schedule

signal finished
signal skip_all

var _cfg: Dictionary
var _id: String
var _elapsed := 0.0
var _showing_result := false
var _done := false
var _scene: Control
var _result: Label


## slot: "월·화", title: 훈련 이름, id: 훈련 id (장면 줄), result/color: 결과 글자와 색 ("대성공! 컨택 +0.5")
func _init(cfg: Dictionary, slot: String, title: String, id: String, result: String, color: Color) -> void:
	_cfg = cfg
	_id = id if TrainingMeta.ROWS.has(id) else "rest"
	mouse_filter = Control.MOUSE_FILTER_STOP
	var cover := ColorRect.new()
	cover.color = Tokens.SCRIM
	cover.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(cover))
	var card := PanelContainer.new()
	card.theme_type_variation = "Card"
	card.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", Tokens.SPACE_SM)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	card.add_child(col)
	var head := HBoxContainer.new()
	head.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var t := _label("%s · %s" % [slot, title], "BodyBold")
	t.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(t)
	var skip := Button.new()
	skip.text = "모두 건너뛰기"
	skip.focus_mode = Control.FOCUS_NONE
	skip.theme_type_variation = "SecondaryButton"
	skip.custom_minimum_size.y = Tokens.TOUCH_MIN - Tokens.SPACE_SM
	skip.pressed.connect(func() -> void: skip_all.emit())
	head.add_child(skip)
	col.add_child(head)
	_scene = Scene.new(_id)
	col.add_child(_scene)
	_result = _label(result, "Title")
	_result.add_theme_color_override("font_color", color)  # 결과마다 색이 달라 변형 하나로는 못 정한다
	_result.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_result.modulate.a = 0.0
	col.add_child(_result)
	card.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	card.grow_vertical = Control.GROW_DIRECTION_BOTH
	add_child(card)


func _process(delta: float) -> void:
	if _done:
		return
	_elapsed += delta
	(_scene as Scene).frame = int(_elapsed * float(_cfg["trainingFps"])) % TrainingMeta.FRAMES
	_scene.queue_redraw()
	if not _showing_result and _elapsed >= float(_cfg["trainingAnimSec"]):
		_show_result()
	elif _showing_result and _elapsed >= float(_cfg["trainingAnimSec"]) + float(_cfg["trainingResultSec"]):
		_finish()


func _gui_input(e: InputEvent) -> void:
	var tapped: bool = (e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT) or (e is InputEventScreenTouch and e.pressed)
	if not tapped:
		return
	accept_event()
	if _showing_result:
		_finish()
	else:
		_elapsed = float(_cfg["trainingAnimSec"])
		_show_result()


func _show_result() -> void:
	_showing_result = true
	_result.pivot_offset = _result.size / 2.0
	_result.scale = Vector2.ONE * 1.4
	var tw := create_tween().set_parallel()
	tw.tween_property(_result, "modulate:a", 1.0, 0.12)
	tw.tween_property(_result, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


func _finish() -> void:
	if _done:
		return
	_done = true
	finished.emit()


static func _label(text: String, variation: String) -> Label:
	var l := Label.new()
	l.text = text
	l.theme_type_variation = variation
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return l


## 도트 장면: 그림 1픽셀을 화면 SCALE 칸으로 (정수배라 픽셀이 뭉개지지 않는다)
class Scene:
	extends Control
	const SCALE := 3
	var row := 0
	var frame := 0

	func _init(id: String) -> void:
		row = TrainingMeta.ROWS[id]
		custom_minimum_size = TrainingMeta.SIZE * SCALE
		texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _draw() -> void:
		var tex := PixelArt.texture("training")
		if tex == null:
			return
		var cell := TrainingMeta.SIZE
		draw_texture_rect_region(tex, Rect2(Vector2.ZERO, cell * SCALE), Rect2(Vector2(frame * cell.x, row * cell.y), cell))
