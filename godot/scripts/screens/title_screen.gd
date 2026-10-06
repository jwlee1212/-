class_name TitleScreen
extends Control
## 타이틀: 새 커리어 / 타격 연습

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var col := UiKit.vbox(Tokens.SPACE_MD)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	var center := CenterContainer.new()
	add_child(UiKit.fill_parent(center))
	center.add_child(col)
	var t := UiKit.title("야구 선수 커리어", Tokens.FONT_HERO, Tokens.PRIMARY)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(t)
	var sub := UiKit.label("(가제) 고교 1학년부터 시작하는 나만의 야구 인생", Tokens.FONT_BODY, Tokens.INK_SOFT)
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(sub)
	var buttons := UiKit.hbox(Tokens.SPACE_MD)
	buttons.alignment = BoxContainer.ALIGNMENT_CENTER
	buttons.add_child(UiKit.button("새 커리어", _app.show_create, Tokens.PRIMARY, Tokens.FONT_TITLE))
	buttons.add_child(UiKit.button("타격 연습", _app.show_practice, Tokens.ACCENT, Tokens.FONT_TITLE))
	col.add_child(buttons)
	var note := UiKit.label("프로토타입 — 타자로만 플레이 (투구·수비는 다음 단계)", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	note.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(note)
