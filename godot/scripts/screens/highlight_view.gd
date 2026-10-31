class_name HighlightView
extends Control
## 경기 하이라이트: 자동 진행 중 점수가 난 안타·홈런·병살을 타구 중계 화면으로 다시 보여 준다 (주자·송구 포함).
## 탭하면 건너뛴다. 끝나면 finished 시그널.
## control(RunControl)을 주면 "내 주루" 장면이 된다: 내 주자에 표시가 붙고, 베이스 버튼(BasePad)이 뜨며, 건너뛸 수 없다.

signal finished

var _phys: BattedBallSim.Config
var _play: PlaySimulator.PlayResult
var _caption: String
var _speed: float
var _us_fielding: bool
var _start_ms := 0.0
var _label: Label
## 내 주루 장면일 때: 판단을 받는 RunControl 과 내 주자의 타순 번호
var control: RunControl = null
var mark_id := -1


func _init(phys: BattedBallSim.Config, play: PlaySimulator.PlayResult, caption: String, speed: float, us_fielding: bool) -> void:
	_phys = phys
	_play = play
	_caption = caption
	_speed = speed
	_us_fielding = us_fielding


func _ready() -> void:
	# 도트 경기장·선수는 칸이 번지지 않게
	if PixelArt.on():
		texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	mouse_filter = Control.MOUSE_FILTER_STOP
	_start_ms = Time.get_ticks_usec() / 1000.0
	var chip := UiKit.glass_panel()
	add_child(chip)
	chip.position = Vector2(Tokens.SPACE_SM, Tokens.SPACE_SM)
	_label = UiKit.label(("" if control != null else "▶ 하이라이트  ") + _caption, Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	chip.add_child(_label)
	if control != null:
		add_child(BasePad.new(func() -> RunControl: return control))
		return
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
		if control == null:
			finished.emit()


func _process(_delta: float) -> void:
	queue_redraw()
	if control != null and control.advance(_t()):
		_play = control.play
	if _t() > _play.duration + 0.6:
		finished.emit()
		set_process(false)


func _t() -> float:
	# 내 주자의 런다운은 느리게 (판단할 틈)
	return _play.play_time((Time.get_ticks_usec() / 1000.0 - _start_ms) / 1000.0 * _speed)


func _draw() -> void:
	BroadcastView.draw(self, Rect2(Vector2.ZERO, size), _phys, _play.ball, _t(), false, _us_fielding, _play, mark_id)
