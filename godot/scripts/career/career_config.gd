class_name CareerConfig
extends RefCounted
## 커리어·경기 수치(balance.json 의 career·game)와 콘텐츠(content.json: 학교·이름·대회·이벤트·대사).

var seed: int
var start: Dictionary
var training: Dictionary = {}  # id -> {label, stat, gain, condition} (json 순서 유지)
var condition_pivot: float
var condition_per_point: float
var growth: Dictionary
var form: Dictionary
var injury: Dictionary
var scout: Dictionary
var season: Dictionary
var teams: Dictionary
var stat_max: int
var event_chance: float
## 이벤트·관계 (career.story), 라이벌, 스카우트 평가·유망주 풀, 고교 평균 타격 (OPS+ 기준)
var story: Dictionary
var rival: Dictionary
var scouting: Dictionary
var league_avg: Dictionary
## 돈 (용돈·칭찬 용돈·상금·교류 비용), 상점 (gear·services), 연애
var economy: Dictionary
var shop: Dictionary
var romance: Dictionary

var innings: int
var auto_step_ms: float
var fast_step_ms: float
var auto_pa: Dictionary
var roster: Dictionary
var running: Dictionary

var my_school: Dictionary
var regional_schools: Array
var national_schools: Array
var tournaments: Array
var surnames: Array
var given_names: Array
var events: Array
var coach_lines: Dictionary
## 게임 이름 (가제) {ko, en, kicker, version}
var game_title: Dictionary
## 고정 인물 (id -> 정의), 가상 프로 구단 [{name, short}], 학년별 챕터 이름 ("1" -> 이름)
var people: Dictionary = {}
var pro_teams: Array
var chapters: Dictionary
## 대화 화면 배경 자리 (id -> 이름)
var places: Dictionary = {}


static func from(balance: Dictionary, content: Dictionary) -> CareerConfig:
	var c := CareerConfig.new()
	var car: Dictionary = balance["career"]
	c.seed = int(BattingConfig.num(car, "seed"))
	c.start = car["start"]
	for id: String in car["training"]:
		if not id.begins_with("_"):
			c.training[id] = car["training"][id]
	c.condition_pivot = BattingConfig.num(car, "conditionEffect.pivot")
	c.condition_per_point = BattingConfig.num(car, "conditionEffect.perPoint")
	c.growth = car["growth"]
	c.form = car["form"]
	c.injury = car["injury"]
	c.scout = car["scout"]
	c.season = car["season"]
	c.teams = car["teams"]
	c.stat_max = int(BattingConfig.num(car, "statMax"))
	c.event_chance = BattingConfig.num(car, "eventChance")
	c.story = car["story"]
	c.rival = car["rival"]
	c.scouting = car["scouting"]
	c.league_avg = car["leagueAvg"]
	c.economy = car["economy"]
	c.shop = car["shop"]
	c.romance = car["romance"]

	var g: Dictionary = balance["game"]
	c.innings = int(BattingConfig.num(g, "innings"))
	c.auto_step_ms = BattingConfig.num(g, "autoStepMs")
	c.fast_step_ms = BattingConfig.num(g, "fastStepMs")
	c.auto_pa = g["autoPa"]
	c.roster = g["roster"]
	c.running = g["running"]

	c.my_school = content["mySchool"]
	c.regional_schools = content["regionalSchools"]
	c.national_schools = content["nationalSchools"]
	c.tournaments = content["tournaments"]
	c.surnames = content["surnames"]
	c.given_names = content["givenNames"]
	c.events = content["events"]
	c.coach_lines = content["coachLines"]
	c.game_title = content["gameTitle"]
	for id: String in content["people"]:
		if not id.begins_with("_"):
			c.people[id] = content["people"][id]
	c.pro_teams = content["proTeams"]
	c.chapters = content["chapters"]
	for id: String in content["places"]:
		if not id.begins_with("_"):
			c.places[id] = content["places"][id]
	return c
