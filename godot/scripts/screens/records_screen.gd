class_name RecordsScreen
extends Control
## 로비의 기록실: 위에 돌아가기 · 제목 · 내 선수, 아래는 허브 기록 탭과 같은 RecordsView

var _app
var view: RecordsView


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var career: CareerState = _app.career
	var root := UiKit.vbox(Tokens.SPACE_SM)
	add_child(UiKit.fill_parent(UiKit.margin(root, Tokens.SPACE_MD)))
	var bar := UiKit.hbox(Tokens.SPACE_MD)
	var back := UiKit.button("‹ 로비", _app.show_title, Tokens.INK_SOFT, Tokens.FONT_CAPTION)
	back.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	bar.add_child(back)
	bar.add_child(Shapes.SlantText.new("기록실", Tokens.FONT_TITLE))
	var gap := Control.new()
	gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.add_child(gap)
	var who := UiKit.label("%s · %s %d학년 · %d번 타자" % [career.player.name, career.my_school.name, career.player.grade, career.player.lineup_slot],
		Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
	who.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	bar.add_child(who)
	root.add_child(bar)
	root.add_child(UiKit.divider())
	view = RecordsView.new(career, _app.batting_config.pitchers)
	root.add_child(view)
