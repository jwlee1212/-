class_name JumpScreen
extends Control
## 테스트 시작 (개발 빌드 전용): 학년과 시점을 고르면 거기까지 자동 진행한 커리어로 바로 플레이한다.
## 유저의 진짜 저장은 건드리지 않는다 — 테스트 커리어는 따로 저장되고(main.gd TEST_SAVE), 로비의 [테스트 끝내기]로 돌아간다.

const MONEY := [[0, "그대로"], [300000, "+30만원"], [1000000, "+100만원"]]
const PARTNERS := [["", "없음(자연스럽게)"], ["manager", "한소율"], ["neighbor", "정다온"], ["reporter", "서지안"]]

var _app
var _grade := 1
var _point := "season_start"
var _money := 0
var _partner := ""
var _name: LineEdit
var _body: VBoxContainer
var _status: Label
var _busy := false


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var center := CenterContainer.new()
	add_child(UiKit.fill_parent(center))
	var card := UiKit.card()
	card.custom_minimum_size.x = 760
	center.add_child(card)
	_body = UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(_body)
	_build()


func _build() -> void:
	for c in _body.get_children():
		c.queue_free()
	_body.add_child(UiKit.kicker("TEST · 개발 빌드 전용", Tokens.ACCENT))
	var head := UiKit.hbox(Tokens.SPACE_MD)
	head.add_child(UiKit.title("원하는 시점부터 시작", Tokens.FONT_TITLE))
	var old_name := _name.text if _name != null else "김도윤"
	_name = LineEdit.new()
	_name.text = old_name
	_name.max_length = 6
	_name.custom_minimum_size.x = 140
	_name.add_theme_font_size_override("font_size", Tokens.FONT_BODY)
	head.add_child(_name)
	_body.add_child(head)
	_body.add_child(_row("학년", [[1, "1학년"], [2, "2학년"], [3, "3학년"]], _grade, func(v: Variant) -> void:
		_grade = v
		if not FastForward.is_valid(_grade, _point):
			_point = "season_end"))
	var points := []
	for p: Array in FastForward.POINTS:
		if FastForward.is_valid(_grade, p[0]):
			points.append(p)
	_body.add_child(_row("시점", points.slice(0, 4), _point, func(v: Variant) -> void: _point = v))
	_body.add_child(_row("", points.slice(4), _point, func(v: Variant) -> void: _point = v))
	_body.add_child(_row("돈", MONEY, _money, func(v: Variant) -> void: _money = v))
	_body.add_child(_row("연애", PARTNERS, _partner, func(v: Variant) -> void: _partner = v))
	var note := UiKit.label("거기까지는 자동 선수가 실제 규칙대로 진행한다 (훈련 돌아가며, 내 타석 자동, 이벤트는 첫 선택지). 도착한 시점의 이야기는 직접 고른다. 내 저장은 그대로 남는다.",
		Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_body.add_child(note)
	_status = UiKit.label("", Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD)
	_body.add_child(_status)
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("로비로", _app.show_title, Tokens.INK_SOFT))
	var go := UiKit.button("이 시점에서 시작 ▶", start, Tokens.ACCENT, Tokens.FONT_LABEL)
	go.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(go)
	_body.add_child(row)


## 고르는 줄: 고른 것만 강조색
func _row(label: String, options: Array, current: Variant, on_pick: Callable) -> Control:
	var row := UiKit.hbox(Tokens.SPACE_XS)
	var l := UiKit.label(label, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
	l.custom_minimum_size.x = 44
	row.add_child(l)
	for o: Array in options:
		var b := UiKit.button(o[1], func() -> void:
			if _busy:
				return
			on_pick.call(o[0])
			_build(), Tokens.PRIMARY if o[0] == current else Tokens.INK_SOFT, Tokens.FONT_CAPTION)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(b)
	return row


## 고른 시점까지 감고 그 화면을 연다 (한 프레임에 한 주씩 — 진행 상황이 보인다)
func start() -> void:
	if _busy:
		return
	_busy = true
	var player_name := _name.text.strip_edges()
	var career := CareerState.new(_app.career_config, player_name if player_name != "" else "김도윤", _app.batting_config)
	var steps := 0
	while not FastForward.reached(career, _grade, _point) and not FastForward.overshot(career, _grade) and steps < FastForward.MAX_STEPS:
		FastForward.step(career)
		steps += 1
		_status.text = "자동 진행 중… %d학년 %s" % [career.player.grade, career.phase_name() + " " + career.round_text() if not career.is_season_over() or career.in_camp() else "시즌 결산"]
		await get_tree().process_frame
	if not FastForward.reached(career, _grade, _point):
		_status.text = "그 시점까지 가지 못했다"
		_busy = false
		return
	FastForward.apply_extras(career, _money, _partner)
	_app.start_test_career(career)
