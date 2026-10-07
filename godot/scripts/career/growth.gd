class_name Growth
extends RefCounted
## 성장 규칙 (balance.json career.growth).
## 실제 오름 = 기본량 × 성장률 × 학년 배율, 성장률 = clamp((잠재력 − 지금) / gapScale, minRate, 1).
## 잠재력에 가까워질수록 느려지고 잠재력을 넘지 못한다. 소수점 성장은 쌓아 두었다가 1이 차면 능력치가 1 오른다.


## 이번 성장량 (소수)
static func amount(cfg: CareerConfig, current: int, potential: int, base: float, grade: int) -> float:
	var g := cfg.growth
	if current >= potential:
		return 0.0
	var rate := clampf(float(potential - current) / float(g["gapScale"]), float(g["minRate"]), 1.0)
	var grades: Array = g["gradeRate"]
	return base * rate * float(grades[clampi(grade - 1, 0, grades.size() - 1)])


## 선수에게 성장 적용. 돌려주는 값: 실제로 오른 능력치 칸 수
static func apply(cfg: CareerConfig, p: PlayerData, stat: String, base: float) -> int:
	var add := amount(cfg, p.stat(stat), p.potential[stat], base, p.grade)
	p.progress[stat] = float(p.progress.get(stat, 0.0)) + add
	var raised := 0
	while p.progress[stat] >= 1.0 and p.stat(stat) < mini(p.potential[stat], cfg.stat_max):
		p.progress[stat] -= 1.0
		p.set(stat, p.stat(stat) + 1)
		raised += 1
	if p.stat(stat) >= p.potential[stat]:
		p.progress[stat] = 0.0
	return raised


## 잠재력 뽑기 (숨김 값)
static func roll_potential(cfg: CareerConfig, current: int, rng: RandomNumberGenerator) -> int:
	var g := cfg.growth
	var v := roundi(rng.randfn(float(g["potentialMean"]), float(g["potentialSd"])))
	return clampi(v, maxi(int(g["potentialMin"]), current + 5), int(g["potentialMax"]))


## 성장 여력을 말로 (정확한 잠재력은 보여 주지 않는다)
static func room_text(current: int, potential: int) -> String:
	var gap := potential - current
	if gap >= 25:
		return "쑥쑥 클 때"
	if gap >= 12:
		return "아직 여유 있음"
	if gap >= 4:
		return "완성 단계"
	return "한계 근처"


## 경기 기록으로 성장 (안타 → 컨택, 장타·홈런 → 파워, 볼넷 → 선구안). 돌려주는 값: {능력치: 오른 칸} (0 은 빼고)
static func from_line(cfg: CareerConfig, p: PlayerData, line: PlayerData.SeasonStats) -> Dictionary:
	var g := cfg.growth
	var grow := {
		"contact": line.hits * float(g["perHitContact"]),
		"power": (line.doubles + line.triples) * float(g["perExtraBasePower"]) + line.home_runs * float(g["perHomeRunPower"]),
		"eye": line.walks * float(g["perWalkEye"]),
	}
	var out := {}
	for stat: String in grow:
		if grow[stat] > 0.0:
			var raised := apply(cfg, p, stat, grow[stat])
			if raised > 0:
				out[stat] = raised
	return out
