class_name HomeTopBar
extends PanelContainer
## 홈 위 바 (읽기 전용 정보): 학년·주차 · 지금 대회와 라운드 | 지갑 | ⚙ 설정 (누르는 것은 ⚙ 하나)

signal settings_pressed

var _when: Label
var _phase: Label
var _wallet: Label


func _init() -> void:
	theme_type_variation = "Bar"
	custom_minimum_size.y = Tokens.TOP_BAR_HEIGHT
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_MD)
	add_child(row)
	_when = _label("", "BarText")
	row.add_child(_when)
	_phase = _label("", "BarTextSoft")
	_phase.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(_phase)
	row.add_child(_label("지갑", "BarTextSoft"))
	_wallet = _label("", "BarText")
	row.add_child(_wallet)
	var gear := Button.new()
	gear.text = "⚙"
	gear.focus_mode = Control.FOCUS_NONE
	gear.theme_type_variation = "BarIconButton"
	gear.custom_minimum_size = Vector2(Tokens.TOUCH_MIN, Tokens.TOUCH_MIN)
	gear.pressed.connect(func() -> void: settings_pressed.emit())
	row.add_child(gear)


func refresh(career: CareerState) -> void:
	var p := career.player
	if career.in_camp():
		_when.text = "%d학년 겨울" % p.grade
		_phase.text = "겨울 캠프 %d/%d주" % [career.camp_week, int(career.cfg.camp["weeks"])]
	else:
		_when.text = "%d학년 · %d주차" % [p.grade, career.week]
		_phase.text = "%s %s" % [career.phase_name(), career.round_text()]
	_wallet.text = PlayerData.money_text(p.money)


static func _label(text: String, variation: String) -> Label:
	var l := Label.new()
	l.text = text
	l.theme_type_variation = variation
	l.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	return l
