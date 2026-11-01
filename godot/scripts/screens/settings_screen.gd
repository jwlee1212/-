class_name SettingsScreen
extends Control
## 설정. 지금은 주루 설정 하나 (자동·보조·수동 — 더쇼의 주루 설정을 따랐다). 고르면 바로 저장된다.

var _app
## [돌아가기]로 갈 화면 (홈 ⚙ 에서 열면 홈, 로비에서 열면 로비)
var _back: Callable
var _buttons := {}
var _note: Label


func _init(app, back: Callable) -> void:
	_app = app
	_back = back


func _ready() -> void:
	var col := UiKit.vbox(Tokens.SPACE_MD)
	add_child(UiKit.fill_parent(UiKit.margin(col, Tokens.SPACE_LG)))
	col.add_child(UiKit.kicker("SETTINGS"))
	col.add_child(UiKit.title("설정", Tokens.FONT_HERO))
	var card := UiKit.card()
	var box := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(box)
	box.add_child(UiKit.label("주루", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	var row := UiKit.hbox(Tokens.SPACE_SM)
	for mode: String in RunControl.MODES:
		var b := UiKit.button(RunControl.MODE_LABELS[mode], _set_running.bind(mode), Tokens.PRIMARY, Tokens.FONT_LABEL)
		row.add_child(b)
		_buttons[mode] = b
	box.add_child(row)
	_note = UiKit.label("", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(_note)
	col.add_child(card)
	col.add_child(UiKit.spacer())
	var back := UiKit.button("‹ 돌아가기", _back, Tokens.INK_SOFT, Tokens.FONT_LABEL)
	back.size_flags_horizontal = Control.SIZE_SHRINK_END  # 왼쪽 아래는 개발 빌드의 ⚙ 손맛 버튼 자리
	col.add_child(back)
	_refresh()


func _set_running(mode: String) -> void:
	_app.settings.running = mode
	_app.settings.save()
	_refresh()


func _refresh() -> void:
	var now: String = _app.settings.running
	for mode: String in _buttons:
		(_buttons[mode] as Button).modulate.a = 1.0 if mode == now else 0.4
	_note.text = RunControl.MODE_NOTES[now]
