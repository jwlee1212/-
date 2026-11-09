class_name SettingsScreen
extends Control
## 설정: 주루 설정 (자동·보조·수동 — 더쇼의 주루 설정을 따랐다) · 튜토리얼 다시 보기. 고르면 바로 저장된다.

var _app
## [돌아가기]로 갈 화면 (홈 ⚙ 에서 열면 홈, 로비에서 열면 로비)
var _back: Callable
var _buttons := {}
var _note: Label
var _tips_note: Label


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
	var tips_card := UiKit.card()
	var tips := UiKit.hbox(Tokens.SPACE_SM)
	tips_card.add_child(tips)
	tips.add_child(UiKit.label("튜토리얼", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	_tips_note = UiKit.label("", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	_tips_note.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tips.add_child(_tips_note)
	tips.add_child(UiKit.button("다시 보기", _reset_tips, Tokens.PRIMARY, Tokens.FONT_LABEL))
	col.add_child(tips_card)
	col.add_child(UiKit.spacer())
	var bottom := UiKit.hbox(Tokens.SPACE_SM)
	bottom.size_flags_horizontal = Control.SIZE_SHRINK_END  # 왼쪽 아래는 개발 빌드의 ⚙ 손맛 버튼 자리
	# 커리어 중(홈 ⚙)에 열었으면 타이틀로 나가는 버튼도 (옛 허브 더보기에 있던 것)
	if _back != Callable(_app, "show_title"):
		bottom.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT, Tokens.FONT_LABEL))
	bottom.add_child(UiKit.button("‹ 돌아가기", _back, Tokens.INK_SOFT, Tokens.FONT_LABEL))
	col.add_child(bottom)
	_refresh()


func _set_running(mode: String) -> void:
	_app.settings.running = mode
	_app.settings.save()
	_refresh()


## 본 팁을 지운다 → 조작을 다시 처음 만날 때 팁 카드가 또 뜬다
func _reset_tips() -> void:
	_app.settings.reset_tips()
	_app.settings.save()
	_refresh()


func _refresh() -> void:
	var now: String = _app.settings.running
	for mode: String in _buttons:
		(_buttons[mode] as Button).modulate.a = 1.0 if mode == now else 0.4
	_note.text = RunControl.MODE_NOTES[now]
	var seen: int = _app.settings.seen_tips.size()
	_tips_note.text = "조작을 처음 만날 때 짧은 팁이 떠요" if seen == 0 else "본 팁 %d개 · 다시 보기를 누르면 처음부터 다시 떠요" % seen
