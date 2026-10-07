class_name Team
extends RefCounted
## 한 경기에 나오는 팀: 타순 9명(이름·능력치·포지션)과 투수진(선발 + 불펜).
## 능력치는 팀 전력을 중심으로 시드 난수로 흩뿌린다 (같은 시드면 같은 선수들).

const POSITIONS := ["중견수", "유격수", "2루수", "1루수", "좌익수", "3루수", "우익수", "포수", "지명타자"]


class Batter:
	var name: String
	var position: String
	var skills: BatterSkills
	var is_me := false
	## 졸업으로 바뀌지 않는 선수 (라이벌)
	var protected := false


class Pitcher:
	var name: String
	## 구위 (삼진을 잘 잡고 강한 타구를 덜 맞는다)
	var stuff: int
	## 제구 (볼넷이 적다)
	var control: int
	## 내 타석에서 쓰는 투구 성향 ("balanced" | "power" | "finesse")
	var profile: String
	## 이 투구 수를 넘으면 바꾼다
	var max_pitches: int


var name: String
var short: String
var lineup: Array[Batter] = []
var pitchers: Array[Pitcher] = []


static func generate(p_name: String, p_short: String, strength: float, starter_profile: String,
		cfg: CareerConfig, rng: RandomNumberGenerator) -> Team:
	var t := Team.new()
	t.name = p_name
	t.short = p_short
	var sd: float = cfg.roster["ratingSd"]
	for i in 9:
		var b := Batter.new()
		b.name = _random_name(cfg, rng)
		b.position = POSITIONS[i]
		b.skills = BatterSkills.new(_rating(strength, sd, rng), _rating(strength, sd, rng), _rating(strength, sd, rng), _rating(strength, sd, rng))
		t.lineup.append(b)
	var profiles := ["balanced", "power", "finesse"]
	for i in int(cfg.roster["bullpenSize"]) + 1:
		var p := Pitcher.new()
		p.name = _random_name(cfg, rng)
		p.stuff = _rating(strength, sd, rng)
		p.control = _rating(strength, sd, rng)
		if i == 0:
			p.profile = starter_profile
			p.max_pitches = int(cfg.roster["starterPitches"]) + rng.randi_range(-int(cfg.roster["starterPitchesJitter"]), int(cfg.roster["starterPitchesJitter"]))
		else:
			p.profile = profiles[rng.randi_range(0, profiles.size() - 1)]
			p.max_pitches = int(cfg.roster["relieverPitches"])
		t.pitchers.append(p)
	return t


## 이번 경기용 팀: 명단은 그대로 두고, 내 선수를 타순 자리에 넣은 복사본 (player 가 null 이면 그대로)
func for_game(player: PlayerData, skills: BatterSkills) -> Team:
	var t := Team.new()
	t.name = name
	t.short = short
	t.lineup = lineup.duplicate()
	t.pitchers = pitchers.duplicate()
	if player != null:
		var me := Batter.new()
		me.name = player.name
		me.skills = skills
		me.position = "외야수"
		me.is_me = true
		t.lineup[player.lineup_slot - 1] = me
	return t


## 졸업: 타자 n_b 명·투수 n_p 명을 새 얼굴로 바꾸고, 남은 선수는 조금 자란다
func graduate(n_b: int, n_p: int, strength: float, cfg: CareerConfig, rng: RandomNumberGenerator) -> void:
	var sd: float = cfg.roster["ratingSd"]
	for b in lineup:
		if b.protected:
			continue
		b.skills = BatterSkills.new(mini(b.skills.contact + 2, 95), mini(b.skills.power + 2, 95), mini(b.skills.eye + 2, 95), b.skills.speed)
	for i in n_b:
		var b := lineup[rng.randi_range(0, lineup.size() - 1)]
		if b.protected:
			continue
		b.name = _random_name(cfg, rng)
		b.skills = BatterSkills.new(_rating(strength, sd, rng), _rating(strength, sd, rng), _rating(strength, sd, rng), _rating(strength, sd, rng))
	for i in n_p:
		var p := pitchers[rng.randi_range(0, pitchers.size() - 1)]
		p.name = _random_name(cfg, rng)
		p.stuff = _rating(strength, sd, rng)
		p.control = _rating(strength, sd, rng)


static func _rating(center: float, sd: float, rng: RandomNumberGenerator) -> int:
	return clampi(roundi(rng.randfn(center, sd)), 15, 95)


static func _random_name(cfg: CareerConfig, rng: RandomNumberGenerator) -> String:
	return cfg.surnames[rng.randi_range(0, cfg.surnames.size() - 1)] + cfg.given_names[rng.randi_range(0, cfg.given_names.size() - 1)]
