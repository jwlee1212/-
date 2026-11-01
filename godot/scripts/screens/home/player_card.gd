class_name PlayerCard
extends PanelContainer
## 내 선수 카드 (얼굴 없음 — 유저가 자기를 넣어 몰입하도록, 2026-11-01): 이름 · 학교·학년·폼 · OVR · 능력치 막대 · 컨디션 게이지.
## 색은 테마 변형(Card · Caption · BodyBold · Number · Kicker), 막대 색은 Tokens.STAT_COLORS

var _career: CareerState


func _init(career: CareerState) -> void:
	_career = career
	theme_type_variation = "Card"
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", Tokens.SPACE_XS)
	add_child(col)
	var p := career.player
	var head := HBoxContainer.new()
	var names := VBoxContainer.new()
	names.add_theme_constant_override("separation", 0)
	names.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	names.add_child(_label(p.name, "BodyBold"))
	var sub := "%s %d학년" % [career.my_school.short, p.grade]
	if absf(p.form) > 0.5:  # 보통이면 안 보인다
		sub += " · " + ("▲ 상승세" if p.form > 0.5 else "▼ 슬럼프")
	names.add_child(_label(sub, "Caption"))
	head.add_child(names)
	var ovr := VBoxContainer.new()
	ovr.add_theme_constant_override("separation", -Tokens.SPACE_XS)
	var k := _label("OVR", "Kicker")
	k.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	ovr.add_child(k)
	var n := _label(str(p.overall()), "Number")
	n.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	ovr.add_child(n)
	head.add_child(ovr)
	col.add_child(head)
	for st: String in PlayerData.STATS:
		col.add_child(_bar_row(PlayerData.STAT_LABELS[st], p.stat(st), 99, Tokens.STAT_COLORS[st], Tokens.SPACE_XS))
	var gap := Control.new()
	gap.custom_minimum_size.y = Tokens.SPACE_XS
	col.add_child(gap)
	# 컨디션은 따로, 굵게: 낮으면 색이 바뀐다
	var cond := p.condition
	col.add_child(_bar_row("컨디션", cond, 100, Tokens.STAT_COLORS["eye"] if cond >= 60 else (Tokens.OUTCOME_COLORS["great"] if cond >= 35 else Tokens.OUTCOME_COLORS["fail"]), Tokens.SPACE_SM))


static func _label(text: String, variation: String) -> Label:
	var l := Label.new()
	l.text = text
	l.theme_type_variation = variation
	return l


static func _bar_row(name: String, value: int, max_value: int, color: Color, thickness: int) -> Control:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_SM)
	var n := _label(name, "Caption")
	n.custom_minimum_size.x = Tokens.SPACE_XL + Tokens.SPACE_SM
	row.add_child(n)
	var g := Gauge.new(float(value) / float(max_value), color, thickness)
	g.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(g)
	var v := _label(str(value), "Caption")
	v.custom_minimum_size.x = Tokens.SPACE_LG
	v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(v)
	return row


## 둥근 막대 하나 (바탕은 구분선 색, 채움은 color)
class Gauge:
	extends Control
	var ratio := 0.0
	var color := Tokens.INK

	func _init(p_ratio: float, p_color: Color, thickness: int) -> void:
		ratio = clampf(p_ratio, 0.0, 1.0)
		color = p_color
		custom_minimum_size.y = thickness
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _draw() -> void:
		var r := size.y / 2.0
		_pill(Rect2(Vector2.ZERO, size), Tokens.DIVIDER, r)
		if ratio > 0.0:
			_pill(Rect2(Vector2.ZERO, Vector2(maxf(size.y, size.x * ratio), size.y)), color, r)

	func _pill(rect: Rect2, c: Color, r: float) -> void:
		var s := StyleBoxFlat.new()
		s.bg_color = c
		s.set_corner_radius_all(int(r))
		s.anti_aliasing = true
		draw_style_box(s, rect)
