class_name School
extends RefCounted
## 학교: 이름·전력·선발 성향과, 시즌 내내 같은 얼굴로 나오는 선수 명단(Team).
## 해마다 전력이 흔들리고 3학년이 졸업해 일부가 새 얼굴로 바뀐다.

var id: String
var name: String
var short: String
var strength: float
var pitcher_profile: String
var team: Team


static func create(id_: String, name_: String, short_: String, strength_: float, profile: String, cfg: CareerConfig, rng: RandomNumberGenerator) -> School:
	var s := School.new()
	s.id = id_
	s.name = name_
	s.short = short_
	s.strength = strength_
	s.pitcher_profile = profile
	s.team = Team.generate(name_, short_, strength_, profile, cfg, rng)
	return s


static func from_content(d: Dictionary, cfg: CareerConfig, rng: RandomNumberGenerator) -> School:
	return create(d["id"], d["name"], d["short"], float(d["strength"]), d["pitcher"], cfg, rng)


## 새 시즌: 전력이 흔들리고, 졸업한 자리에 새 선수가 들어온다. 남은 선수는 한 해 자란다
func new_season(cfg: CareerConfig, rng: RandomNumberGenerator) -> void:
	strength = clampf(strength + rng.randfn(0.0, float(cfg.teams["yearlyDriftSd"])), 30.0, 75.0)
	team.graduate(int(cfg.teams["graduateBatters"]), int(cfg.teams["graduatePitchers"]), strength, cfg, rng)
