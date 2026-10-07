class_name AutoPa
extends RefCounted
## 다른 타자들의 타석 자동 진행.
## 1. 삼진·볼넷: 타자 성향과 투수 성향을 리그 평균 기준으로 합친다 (Log5)
## 2. 나머지는 공이 맞는다: 타구 속도(타자 파워 − 투수 구위)·발사각·방향을 뽑아 내 타석과 같은 타구 물리·수비로 결과를 낸다
## 돌려주는 값: {"outcome", "ball" (BattedBallSim.Result 또는 null), "pitches"}


static func simulate(a: Dictionary, bc: BattingConfig, batter: BatterSkills, pitcher: Team.Pitcher, rng: RandomNumberGenerator) -> Dictionary:
	var k := log5(_lerp(a, "batterK", batter.contact), _lerp(a, "pitcherK", pitcher.stuff), float(a["leagueK"]))
	var bb := log5(_lerp(a, "batterBB", batter.eye), _lerp(a, "pitcherBB", pitcher.control), float(a["leagueBB"]))
	var jitter: float = a["pitchesJitter"]
	var roll := rng.randf()
	if roll < k:
		return {"outcome": SwingJudge.Outcome.STRIKEOUT, "ball": null, "pitches": _pitches(float(a["pitchesPerK"]), jitter, rng)}
	if roll < k + bb:
		return {"outcome": SwingJudge.Outcome.WALK, "ball": null, "pitches": _pitches(float(a["pitchesPerBB"]), jitter, rng)}
	var ev := rng.randfn(_lerp(a, "evMean", batter.power) + (pitcher.stuff - 50) * float(a["evPerPitcherStuff"]), float(a["evSd"]))
	var la := rng.randfn(float(a["launchMeanDeg"]), float(a["launchSdDeg"]))
	var spray := clampf(rng.randfn(float(a["sprayMeanDeg"]), float(a["spraySdDeg"])), -44.0, 44.0)
	var ball := BattedBallSim.simulate(bc.ball_physics, maxf(ev, 30.0), la, spray, batter.speed)
	return {"outcome": ball.outcome, "ball": ball, "pitches": _pitches(float(a["pitchesPerBallInPlay"]), jitter, rng)}


## 타자 확률 b, 투수 확률 p, 리그 평균 l 을 합친 확률
static func log5(b: float, p: float, l: float) -> float:
	var num := b * p / l
	return num / (num + (1.0 - b) * (1.0 - p) / (1.0 - l))


static func _lerp(a: Dictionary, key: String, rating: int) -> float:
	return lerpf(float(a[key + "At0"]), float(a[key + "At100"]), clampi(rating, 0, 100) / 100.0)


static func _pitches(mean: float, jitter: float, rng: RandomNumberGenerator) -> int:
	return maxi(1, roundi(rng.randfn(mean, jitter)))
