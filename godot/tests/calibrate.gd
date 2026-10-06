extends SceneTree
## 가상 타자로 타석을 많이 돌려 수치 균형을 본다 (튜닝용, 테스트 아님).
##   ~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/calibrate.gd
##
## 가상 타자 (사람 흉내):
## - 기본은 직구 타이밍(그 투수의 직구 평균 비행 시간)을 기다린다
## - 구종이 드러난 뒤 남은 시간이 REACTION_MS 이상이면 그 구종의 평균 타이밍으로 고친다. 모자라면 속는다
## - 같은 구종 안의 속도 흔들림(jitter)은 모른다
## - 탭 오차는 정규분포(표준편차 TIMING_SD_MS)
## - 스트라이크는 SWING_STRIKE 확률로, 존 밖 공은 SWING_BALL 확률로 휘두른다 (노려치기는 쓰지 않음)

const AT_BATS := 4000
## 폰 터치 지연의 흔들림까지 넣은 사람의 탭 오차 가정 (맥 키보드·마우스라면 30 정도)
const TIMING_SD_MS := 45.0
const SWING_STRIKE := 0.8
const SWING_BALL := 0.3
const REACTION_MS := 250.0


func _init() -> void:
	var config := BattingConfig.from_balance(BalanceLoader.load_balance())
	var rng := RandomNumberGenerator.new()
	rng.seed = 1
	print("가상 타자: 타이밍 오차 ±%dms, 스트라이크 스윙 %d%%, 볼 스윙 %d%%, %d타석씩, 밸런스 스윙" % [TIMING_SD_MS, SWING_STRIKE * 100, SWING_BALL * 100, AT_BATS])
	print("목표(한국 프로야구 평균 근처): 타율 .260~.280, BABIP .290~.320, 삼진 17~21%, 볼넷 8~10%, 홈런 2~3%, 땅볼 43% 라이너 20% 뜬공 37% 안팎")
	print("투수     능력  타율  출루  장타  삼진%  볼넷%  홈런%  BABIP 땅볼% 라이너% 뜬공% 2루타/안타")
	for pid in ["balanced"]:
		for level in [30, 50, 70, 90]:
			_run(config, config.pitchers[pid], BatterSkills.new(level, level, level, 50), rng)
	for pid in ["power", "finesse"]:
		_run(config, config.pitchers[pid], BatterSkills.new(50, 50, 50, 50), rng)
	quit()


func _run(config: BattingConfig, pitcher: BattingConfig.PitcherProfile, skills: BatterSkills, rng: RandomNumberGenerator) -> void:
	var c := {"pa": 0, "ab": 0, "h": 0, "tb": 0, "bb": 0, "k": 0, "hr": 0, "d": 0, "bip": 0, "bip_h": 0, "gb": 0, "ld": 0, "fb": 0}
	for i in AT_BATS:
		var at_bat := AtBat.new(config, skills, pitcher, i + 1)
		while not at_bat.is_over():
			var p := at_bat.next_pitch()
			var swing_chance := SWING_STRIKE if p.is_strike() else SWING_BALL
			if rng.randf() < swing_chance:
				at_bat.swing(_expected_ms(config, pitcher, p) + config.input_latency_ms + rng.randfn(0.0, TIMING_SD_MS))
			else:
				at_bat.take()
		var r := at_bat.result
		var o := r.outcome
		c.pa += 1
		if o == SwingJudge.Outcome.WALK:
			c.bb += 1
			continue
		c.ab += 1
		if o == SwingJudge.Outcome.STRIKEOUT:
			c.k += 1
			continue
		if SwingJudge.is_hit(o):
			c.h += 1
			c.tb += [1, 2, 3, 4][[SwingJudge.Outcome.SINGLE, SwingJudge.Outcome.DOUBLE, SwingJudge.Outcome.TRIPLE, SwingJudge.Outcome.HOME_RUN].find(o)]
		if o == SwingJudge.Outcome.DOUBLE:
			c.d += 1
		if o == SwingJudge.Outcome.HOME_RUN:
			c.hr += 1
		else:
			c.bip += 1
			if SwingJudge.is_hit(o):
				c.bip_h += 1
		match r.batted_ball:
			SwingJudge.BattedBall.GROUND: c.gb += 1
			SwingJudge.BattedBall.LINE: c.ld += 1
			SwingJudge.BattedBall.FLY: c.fb += 1
	var batted: int = maxi(1, c.gb + c.ld + c.fb)
	print("%-6s %4d  %.3f %.3f %.3f %5.1f %5.1f %5.1f  %.3f %5.1f %5.1f %5.1f  %.2f" % [pitcher.name, skills.contact,
		float(c.h) / c.ab, float(c.h + c.bb) / c.pa, float(c.tb) / c.ab,
		100.0 * c.k / c.pa, 100.0 * c.bb / c.pa, 100.0 * c.hr / c.pa, float(c.bip_h) / maxi(1, c.bip),
		100.0 * c.gb / batted, 100.0 * c.ld / batted, 100.0 * c.fb / batted, float(c.d) / maxi(1, c.h)])


## 사람이 예상하는 도착 시각
func _expected_ms(config: BattingConfig, pitcher: BattingConfig.PitcherProfile, p: Pitch) -> float:
	var fastball: float = (config.pitches["fastball"] as BattingConfig.PitchSpec).flight_ms + pitcher.speed_ms
	var actual_mean: float = (config.pitches[p.type] as BattingConfig.PitchSpec).flight_ms + pitcher.speed_ms
	var time_after_reveal := (1.0 - p.reveal_fraction) * p.flight_ms
	return actual_mean if time_after_reveal >= REACTION_MS else fastball
