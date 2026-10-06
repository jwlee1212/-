class_name CareerConfig
extends RefCounted
## 커리어·경기 수치(balance.json 의 career·game)와 콘텐츠(content.json: 학교·이름·이벤트).

var season_weeks: int
var seed: int
var start: Dictionary
var training: Dictionary = {}  # id -> {label, stat, gain, condition} (json 순서 유지)
var condition_pivot: float
var condition_per_point: float
var growth: Dictionary
var scout: Dictionary
var stat_max: int
var event_chance: float

var innings: int
var auto_step_ms: float
var fast_step_ms: float
var outcome_weights: Dictionary = {}  # SwingJudge.Outcome -> 가중치
var strength_hit_mul_per_point: float
var running: Dictionary
var my_team_strength: float

var my_school: Dictionary
var opponents: Array
var surnames: Array
var given_names: Array
var events: Array
var coach_lines: Dictionary


static func from(balance: Dictionary, content: Dictionary) -> CareerConfig:
	var c := CareerConfig.new()
	var car: Dictionary = balance["career"]
	c.season_weeks = int(BattingConfig.num(car, "seasonWeeks"))
	c.seed = int(BattingConfig.num(car, "seed"))
	c.start = car["start"]
	for id: String in car["training"]:
		if not id.begins_with("_"):
			c.training[id] = car["training"][id]
	c.condition_pivot = BattingConfig.num(car, "conditionEffect.pivot")
	c.condition_per_point = BattingConfig.num(car, "conditionEffect.perPoint")
	c.growth = car["gameGrowth"]
	c.scout = car["scout"]
	c.stat_max = int(BattingConfig.num(car, "statMax"))
	c.event_chance = BattingConfig.num(car, "eventChance")

	var g: Dictionary = balance["game"]
	c.innings = int(BattingConfig.num(g, "innings"))
	c.auto_step_ms = BattingConfig.num(g, "autoStepMs")
	c.fast_step_ms = BattingConfig.num(g, "fastStepMs")
	for key: String in g["outcomeWeights"]:
		var o: int = SwingJudge.Outcome.keys().find(key)
		assert(o >= 0, "balance.json: game.outcomeWeights 의 %s 는 없는 결과" % key)
		c.outcome_weights[o] = BattingConfig.num(g, "outcomeWeights." + key)
	c.strength_hit_mul_per_point = BattingConfig.num(g, "strengthHitMulPerPoint")
	c.running = g["running"]
	c.my_team_strength = BattingConfig.num(g, "myTeamStrength")

	c.my_school = content["mySchool"]
	c.opponents = content["opponents"]
	c.surnames = content["surnames"]
	c.given_names = content["givenNames"]
	c.events = content["events"]
	c.coach_lines = content["coachLines"]
	return c
