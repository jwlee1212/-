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
## 기록실 (분할 기록 기준)
var records: Dictionary
## 돈 (용돈·칭찬 용돈·상금·교류 비용), 상점 (gear·services), 연애
var economy: Dictionary
var shop: Dictionary
var romance: Dictionary
## 겨울 캠프 (주 수, 연습경기 주차, 훈련 보너스)
var camp: Dictionary
## 주간 스케줄 (평일 칸, 대성공·성공·실패 확률)
var schedule: Dictionary
## 입부 체력 테스트 미니게임 수치 (balance.json career.fitness)
var fitness: Dictionary
## 미니게임 글 (content.json minigames: id -> 제목·종목 설명·감독 한마디)
var minigames: Dictionary = {}
## 폰 (한 주 문자 수, 답장 기한, 말풍선 연출 시간)
var phone: Dictionary
## 폰 뉴스: 수치(balance.json career.news)와 문장 틀(content.json news)
var news_cfg: Dictionary
var news: Dictionary = {}

var innings: int
var auto_step_ms: float
var fast_step_ms: float
var auto_pa: Dictionary
var roster: Dictionary
var running: Dictionary
## 도루 (투수 동작·수비 시간)
var steal: Dictionary
## 내 수비 (유격수 포구·송구 미니게임) — balance.json game.fielding
var fielding: Dictionary

var my_school: Dictionary
var regional_schools: Array
var national_schools: Array
## 리그·대회에는 안 나오고 연습경기 상대로만 쓰는 팀 (대학팀 등 — content.json exhibitionSchools)
var exhibition_schools: Array = []
var tournaments: Array
var surnames: Array
var given_names: Array
var events: Array
## 아직 세우는 이벤트가 없는 플래그 (content.json pendingFlags — 검증에서 requires 로 써도 된다)
var pending_flags: Array = []
## 아직 없는 이벤트 id (content.json pendingEvents — schedule 대상으로 써도 된다, 예약은 이벤트가 들어올 때까지 쉰다)
var pending_events: Array = []
var coach_lines: Dictionary
## 게임 이름 (가제) {ko, en, kicker, version}
var game_title: Dictionary
## 고정 인물 (id -> 정의), 가상 프로 구단 [{name, short}], 학년별 챕터 이름 ("1" -> 이름)
var people: Dictionary = {}
var pro_teams: Array
var chapters: Dictionary
## 대화 화면 배경 자리 (id -> 이름)
var places: Dictionary = {}
## 폰 문자 (content.json messages), 단체방 (phone.rooms: id -> {name})
var messages: Array = []
var phone_rooms: Dictionary = {}
## 시나리오 변수 선언 (content.json vars: id -> {"type": int|bool|enum|string|list, "default", "values"?(enum)}).
## 플래그(flags)와 달리 값이 있는 이야기 상태 — 호감도·테스트 등급·경기 장면 결과 (docs/scenario 문서의 ID 그대로)
var vars: Dictionary = {}
## 이야기 속 연습 미니게임 (이벤트 효과 minigame "drill:<id>"): content.json drills(제목·결과 변수) 에
## balance.json career.drills(공 수·성장량·보정) 를 겹친 것. id -> 정의
var drills: Dictionary = {}


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
	c.records = car["records"]
	c.economy = car["economy"]
	c.shop = car["shop"]
	c.romance = car["romance"]
	c.camp = car["camp"]
	c.schedule = car["schedule"]
	assert(not (c.schedule["slots"] as Array).is_empty(), "balance.json: career.schedule.slots 가 비었다")
	c.phone = car["phone"]
	c.news_cfg = car["news"]
	c.fitness = car["fitness"]

	var g: Dictionary = balance["game"]
	c.innings = int(BattingConfig.num(g, "innings"))
	c.auto_step_ms = BattingConfig.num(g, "autoStepMs")
	c.fast_step_ms = BattingConfig.num(g, "fastStepMs")
	c.auto_pa = g["autoPa"]
	c.roster = g["roster"]
	c.running = g["running"]
	c.steal = g["steal"]
	c.fielding = g["fielding"]

	c.my_school = content["mySchool"]
	c.regional_schools = content["regionalSchools"]
	c.national_schools = content["nationalSchools"]
	c.exhibition_schools = content.get("exhibitionSchools", [])
	c.tournaments = content["tournaments"]
	c.surnames = content["surnames"]
	c.given_names = content["givenNames"]
	c.events = content["events"]
	EventBook.prepare(c.events)
	c.pending_flags = content.get("pendingFlags", [])
	c.pending_events = content.get("pendingEvents", [])
	c.coach_lines = content["coachLines"]
	c.minigames = content.get("minigames", {})
	c.game_title = content["gameTitle"]
	for id: String in content["people"]:
		if not id.begins_with("_"):
			c.people[id] = content["people"][id]
	c.pro_teams = content["proTeams"]
	c.chapters = content["chapters"]
	for id: String in content["places"]:
		if not id.begins_with("_"):
			c.places[id] = content["places"][id]
	c.news = content.get("news", {})
	c.messages = content.get("messages", [])
	var drill_numbers: Dictionary = car.get("drills", {})
	for id: String in content.get("drills", {}):
		if not id.begins_with("_"):
			var d: Dictionary = (content["drills"][id] as Dictionary).duplicate(true)
			d.merge(drill_numbers.get(id, {}), true)
			c.drills[id] = d
	for id: String in content.get("vars", {}):
		if not id.begins_with("_"):
			c.vars[id] = content["vars"][id]
	for id: String in content.get("phone", {}).get("rooms", {}):
		c.phone_rooms[id] = content["phone"]["rooms"][id]
	return c
