class_name Rival
extends RefCounted
## 라이벌: 라이벌 학교(content.json people.rival.school) 명단의 lineupSlot 번 타자.
## 나와 같은 학년의 PlayerData 라서 같은 성장 규칙으로 자란다. 학교 명단의 그 자리 선수(Team.Batter)가 곧 라이벌이라
## 그 학교와 붙으면 실제로 내 경기에 나와 친다(박스스코어 기록이 그대로 라이벌 기록이 된다).
## 안 붙는 주에는 평균 투수 상대로 몇 타석을 자동 진행해 기록을 쌓는다.

var player: PlayerData
var school_id: String
## 학교 명단 속 라이벌 자리 (능력치를 매주 맞춰 준다)
var batter: Team.Batter
## 지난 주 경기 기록 (없으면 null)
var last_line: PlayerData.SeasonStats = null
## 지난 주 나와 붙었나
var last_vs_me := false


static func create(cfg: CareerConfig, school: School, rng: RandomNumberGenerator) -> Rival:
	var r := Rival.new()
	var rc := cfg.rival
	r.school_id = school.id
	r.player = PlayerData.new()
	r.player.name = Team._random_name(cfg, rng)
	for s: String in PlayerData.STATS:
		r.player.set(s, int(rc["start"][s]))
		r.player.potential[s] = int(rc["potential"][s])
	r.player.mark_season_start()
	r.batter = school.team.lineup[int(rc["lineupSlot"]) - 1]
	r.batter.name = r.player.name
	r.batter.position = "3루수"
	r.batter.protected = true
	r.sync_batter()
	return r


## 명단 속 라이벌 능력치를 지금 능력치로
func sync_batter() -> void:
	var p := player
	batter.skills = BatterSkills.new(p.contact, p.power, p.eye, p.speed)


## 한 주: 훈련 성장 + 경기 기록·성장. game 이 라이벌 학교와의 경기면 그 박스스코어 줄을 쓴다
func play_week(cfg: CareerConfig, bc: BattingConfig, game: GameRunner, rng: RandomNumberGenerator) -> void:
	var rc := cfg.rival
	# 훈련: 능력치를 돌아가며
	var stat: String = PlayerData.STATS[rng.randi_range(0, PlayerData.STATS.size() - 1)]
	Growth.apply(cfg, player, stat, float(rc["weeklyTrainingGain"]))
	last_vs_me = game != null and game.opponent.id == school_id
	if last_vs_me:
		var side := 1 - game.my_side
		last_line = PlayerData.SeasonStats.from_box(game.box.batters[side][int(rc["lineupSlot"]) - 1])
	else:
		last_line = simulate_line(cfg, bc, rng)
	player.season.merge(last_line)
	Growth.from_line(cfg, player, last_line)
	var s := cfg.scout
	player.add_scout(last_line.hits * int(s["perHit"]) + last_line.home_runs * int(s["perHomeRun"]) + last_line.strikeouts * int(s["perStrikeout"]))
	sync_batter()


## 평균 투수 상대 자동 타석 몇 개
func simulate_line(cfg: CareerConfig, bc: BattingConfig, rng: RandomNumberGenerator) -> PlayerData.SeasonStats:
	var rc := cfg.rival
	var line := PlayerData.SeasonStats.new()
	line.games = 1
	var pitcher := Team.Pitcher.new()
	pitcher.stuff = Team._rating(float(rc["pitcherStrength"]), float(cfg.roster["ratingSd"]), rng)
	pitcher.control = Team._rating(float(rc["pitcherStrength"]), float(cfg.roster["ratingSd"]), rng)
	var skills := BatterSkills.new(player.contact, player.power, player.eye, player.speed)
	for i in int(rc["paPerGame"]):
		var r := AutoPa.simulate(cfg.auto_pa, bc, skills, pitcher, rng)
		var o: SwingJudge.Outcome = r["outcome"]
		var rbi := 0
		if o == SwingJudge.Outcome.HOME_RUN:
			rbi = 1
		elif SwingJudge.is_hit(o) and rng.randf() < float(rc["rbiPerHitChance"]):
			rbi = 1
		line.add(o, rbi)
	return line


## 새 시즌: 학년이 오르고 겨울 훈련, 기록은 새로
func new_season(cfg: CareerConfig) -> void:
	for s in PlayerData.STATS:
		Growth.apply(cfg, player, s, float(cfg.growth["winterGain"]))
	player.grade = mini(player.grade + 1, 3)
	player.season = PlayerData.SeasonStats.new()
	player.mark_season_start()
	last_line = null
	sync_batter()
