class_name HighlightView
extends Control
## 경기 하이라이트: 자동 진행 중 점수가 난 안타·홈런·병살을 타구 중계 화면으로 다시 보여 준다 (주자·송구 포함).
## 탭하면 건너뛴다. 끝나면 finished 시그널.

signal finished

var _phys: BattedBallSim.Config
var _play: PlaySimulator.PlayResult
var _caption: String
var _speed: float
var _us_fielding: bool
var _start_ms := 0.0
var _label: Label


func _init(phys: BattedBallSim.Config, play: PlaySimulator.PlayResult, caption: String, speed: float, us_fielding: bool) -> void:
	_phys = phys
	_play = play
	_caption = caption
	_speed = speed
	_us_fielding = us_fielding


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	_start_ms = Time.get_ticks_usec() / 1000.0
	var chip := UiKit.glass_panel()
	add_child(chip)
	chip.position = Vector2(Tokens.SPACE_SM, Tokens.SPACE_SM)
	_label = UiKit.label("▶ 하이라이트  " + _caption, Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	chip.add_child(_label)
	var hint := UiKit.label("탭해서 건너뛰기", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
	var hint_chip := UiKit.glass_panel()
	hint_chip.add_child(hint)
	add_child(hint_chip)
	hint_chip.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	hint_chip.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	hint_chip.grow_vertical = Control.GROW_DIRECTION_BEGIN


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed:
		accept_event()
		finished.emit()


func _process(_delta: float) -> void:
	queue_redraw()
	if _t() > _play.duration + 0.6:
		finished.emit()
		set_process(false)


func _t() -> float:
	return (Time.get_ticks_usec() / 1000.0 - _start_ms) / 1000.0 * _speed


func _draw() -> void:
	BroadcastView.draw(self, Rect2(Vector2.ZERO, size), _phys, _play.ball, _t(), false, _us_fielding, _play)
