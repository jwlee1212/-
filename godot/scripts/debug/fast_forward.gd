class_name FastForward
extends RefCounted
## 테스트용 빨리 감기 (개발 빌드의 "테스트 시작"): 커리어를 원하는 시점까지 실제 규칙대로 자동 진행한다.
## 상태를 억지로 건너뛰지 않아서 기록·이벤트·관계·저장이 정상 플레이와 똑같이 맞는다.
##
## 자동 선수: 컨디션이 50 아래면 휴식, 아니면 훈련을 돌아가며. 내 타석은 다른 타자처럼 자동 타석(AutoPa).
## 이벤트는 고를 수 있는 첫 선택지. 난수는 커리어 시드에서 나온다 — 같은 시점을 고르면 같은 커리어가 된다 (원칙 2).
##
## 시점(point): season_start 시즌 시작 | phase:1 첫 전국대회 | phase:2 주말리그 후반기 | phase:3 두 번째 전국대회
##   | season_end 시즌 결산 | camp 겨울 캠프 1주 | camp_game 캠프 첫 연습경기 주 | camp_end 캠프 결산
## 도착한 시점의 이야기(주 시작·시즌 끝)는 고르지 않고 남겨 둔다 — 유저가 직접 본다.

const POINTS := [
	["season_start", "시즌 시작"], ["phase:1", "첫 전국대회"], ["phase:2", "주말리그 후반기"], ["phase:3", "두 번째 전국대회"], ["phase:4", "세 번째 전국대회"],
	["season_end", "시즌 결산"], ["camp", "겨울 캠프 1주"], ["camp_game", "캠프 연습경기 주"], ["camp_end", "캠프 결산"]]
const TRAININGS := ["contact", "power", "eye", "speed", "fielding"]
## 한 번에 최대 몇 주까지 감나 (3년 = 80주 안쪽)
const MAX_STEPS := 200


## 3학년 겨울은 캠프가 없다
static func is_valid(grade: int, point: String) -> bool:
	return grade >= 1 and grade <= 3 and not (grade == 3 and point.begins_with("camp"))


static func reached(career: CareerState, grade: int, point: String) -> bool:
	if career.player.grade != grade or career.game_done:
		return false
	match point:
		"season_start":
			return not career.is_season_over() and career.phase_index == 0 and career.phase_game == 0
		"season_end":
			return career.is_season_over() and career.camp_week == 0
		"camp":
			return career.camp_week == 1
		"camp_game":
			return career.in_camp() and career.camp_has_game()
		"camp_end":
			return career.camp_finished()
	if point.begins_with("phase:"):
		return not career.is_season_over() and career.phase_index == int(point.trim_prefix("phase:")) and career.phase_game == 0
	return false


## 목표를 지나쳤나 (더 감아도 못 간다)
static func overshot(career: CareerState, grade: int) -> bool:
	return career.player.grade > grade


## 목표 시점까지 감는다. 돌려주는 값: 도착했나
static func run(career: CareerState, grade: int, point: String) -> bool:
	for i in MAX_STEPS:
		if reached(career, grade, point):
			return true
		if overshot(career, grade):
			return false
		step(career)
	return reached(career, grade, point)


## 한 걸음: 시즌·캠프의 한 주, 또는 시즌 결산 → 캠프 시작, 캠프 결산 → 다음 시즌
static func step(career: CareerState) -> void:
	if career.in_camp():
		_resolve(career, "start")
		_train(career)
		if career.camp_has_game():
			_play(career)
		else:
			career.finish_camp_week()
		_resolve(career, "after")
		career.advance_week()
	elif career.camp_finished():
		career.winter_training()
		career.start_next_season()
	elif career.is_season_over():
		_resolve(career, "seasonEnd")
		if career.player.grade < 3:
			career.start_camp()
	else:
		_resolve(career, "start")
		_train(career)
		_play(career)
		_resolve(career, "after")
		career.advance_week()


static func _train(career: CareerState) -> void:
	career.train("rest" if career.player.condition < 50 else TRAININGS[career.total_week % TRAININGS.size()])


## 이번 주 경기를 자동으로 (내 타석도 자동 타석 — 기록실 분할 기록이 차도록 타구와 카운트를 붙인다)
static func _play(career: CareerState) -> void:
	var game := career.new_game()
	var rng := career._rng("fast-forward")
	while not game.state.over:
		# 경기 대본의 장면에 도착했으면 장면 대화부터 (수비 장면은 내 타석이 아니라 여기서 넘긴다)
		if game.in_scene():
			career.auto_game_story(game)
		if game.step()["type"] == "my_turn":
			career.auto_game_story(game)
			var pa := AutoPa.simulate(career.cfg.auto_pa, career.batting, game.current_batter().skills, game.current_pitcher(), rng)
			var c: SwingJudge.Contact = null
			if pa["ball"] != null:
				var ball: BattedBallSim.Result = pa["ball"]
				c = SwingJudge.Contact.new(SwingJudge.Quality.SOLID, ball.spray_deg, ball.distance_m, ball.batted_ball)
				c.ball = ball
			var res := AtBat.Result.new(pa["outcome"], SwingJudge.BattedBall.NONE, pa["pitches"], c)
			res.balls = rng.randi_range(0, 3)
			res.strikes = rng.randi_range(0, 2)
			game.apply_my_result(res)
	career.finish_game(game)


## 이 시점의 이야기를 고를 수 있는 첫 선택지로 넘긴다
static func _resolve(career: CareerState, timing: String) -> void:
	# 이야기 하나가 끝나면 그다음 이야기가 열릴 수 있다 (입학 첫날처럼) — 남은 게 없을 때까지
	while not career.events(timing).is_empty():
		var e: Dictionary = career.events(timing)[0]
		var choices: Array = e.get("choices", [])
		var pick := 0
		for i in choices.size():
			if EventBook.choice_block(career, choices[i]) == "":
				pick = i
				break
		career.resolve_event(e, pick)


## 테스트 편의: 돈을 더하고, 연애 상대를 정한다 ("" 이면 그대로)
static func apply_extras(career: CareerState, money: int, partner: String) -> void:
	career.player.money += money
	if partner != "" and career.people.has(partner):
		career.people.meet(partner)
		career.people.partner = partner
		career.people.by_id[partner].relation = maxi(career.people.relation(partner), 80)
