class_name CreateScreen
extends Control
## 선수 만들기: 이름만 정한다 (폰에서 키보드를 안 띄워도 되게 추천 이름 버튼을 둔다)

var _app
var _name_edit: LineEdit


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var center := CenterContainer.new()
	add_child(UiKit.fill_parent(center))
	var card := UiKit.card()
	card.custom_minimum_size.x = 520
	center.add_child(card)
	var col := UiKit.vbox(Tokens.SPACE_MD)
	card.add_child(col)
	var school: String = _app.career_config.my_school["name"]
	col.add_child(UiKit.title("%s 야구부 입단" % school))
	col.add_child(UiKit.label("약체 야구부에 들어온 1학년. 포지션은 외야수(타자).", Tokens.FONT_BODY, Tokens.INK_SOFT))
	_name_edit = LineEdit.new()
	_name_edit.placeholder_text = "이름"
	_name_edit.max_length = 6
	var cfg: CareerConfig = _app.career_config
	_name_edit.text = cfg.surnames[0] + cfg.given_names[0]
	col.add_child(_name_edit)
	var chips := UiKit.hbox(Tokens.SPACE_XS)
	for i in 4:
		var n: String = cfg.surnames[(i * 7 + 3) % cfg.surnames.size()] + cfg.given_names[(i * 5 + 2) % cfg.given_names.size()]
		chips.add_child(UiKit.button(n, func() -> void: _name_edit.text = n, Tokens.INK_SOFT, Tokens.FONT_CAPTION))
	col.add_child(chips)
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("뒤로", _app.show_title, Tokens.INK_SOFT))
	var go := UiKit.button("입학하기", func() -> void:
		var n := _name_edit.text.strip_edges()
		_app.start_career(n if n != "" else "신인"), Tokens.PRIMARY)
	go.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(go)
	col.add_child(row)
