class_name PitchHint
extends RefCounted
## 소율의 구종 힌트 (폰 문자 m_y1_soyul_hint 의 답장 보상, 2026-11-05). 로직만 — 화면은 BattingHud 가 memo 글자를 받아 그린다.
##
## 답장하면 플레이어 플래그 FLAG 가 켜진다 (문자 효과 flag). 다음 경기 하나에만 쓴다:
##   - 그 경기의 내 첫 타석이 시작될 때 상대 투수의 결정구를 타석 HUD 에 짧게 보인다 ("소율 메모: 2S 이후 슬라이더")
##   - 경기가 끝나면 CareerState.finish_game 이 플래그를 끈다 (타석에 안 섰어도 — 경기 하나가 지나갔으니)
## 플래그라서 저장·불러오기에 그대로 남고, 옛 저장 파일은 플래그가 없을 뿐이라 호환된다.
## 판정(swing_judge·at_bat)과 볼배합(pitch_caller)은 건드리지 않는다 — 보이기만 한다.
##
## 결정구 = 볼배합에서 2스트라이크 이후 가장 많이 던지는 구종. 굴려 보지 않고 볼배합 수치로 기대 비율을 바로 계산한다
## (같은 투수면 언제나 같은 답 — 난수 없음):
##   2스트라이크 카운트(0-2·1-2·2-2·3-2)마다
##     결정구 확률(twoStrikePutawayChance) × [그 투수의 putaway 구종]
##     + (1 − 결정구 확률) × (상황별 가중치 × 투수 배율을 합 1 로 나눈 비율)
##   을 네 카운트 평균한 비율이 가장 큰 구종. 같으면 구종 목록(balance.json 순서) 앞의 것

const FLAG := "pitch_hint"
const TWO_STRIKE_COUNTS := [[0, 2], [1, 2], [2, 2], [3, 2]]


## 2스트라이크 이후 구종별 기대 비율 (구종 id -> 0~1, 합 1)
static func two_strike_shares(config: BattingConfig, pitcher: BattingConfig.PitcherProfile) -> Dictionary:
	var shares := {}
	for id: String in config.pitches:
		shares[id] = 0.0
	var putaway := config.two_strike_putaway_chance
	for count: Array in TWO_STRIKE_COUNTS:
		var situation: BattingConfig.Situation = config.situations[BattingConfig.situation_key(count[0], count[1])]
		var total := 0.0
		for id: String in config.pitches:
			total += float(situation.mix[id]) * float(pitcher.mix[id])
		for id: String in config.pitches:
			var w := float(situation.mix[id]) * float(pitcher.mix[id])
			shares[id] += (1.0 - putaway) * (w / total if total > 0.0 else 0.0) / TWO_STRIKE_COUNTS.size()
		if shares.has(pitcher.putaway_pitch):
			shares[pitcher.putaway_pitch] += putaway / TWO_STRIKE_COUNTS.size()
	return shares


## 결정구 구종 id
static func putaway_type(config: BattingConfig, pitcher: BattingConfig.PitcherProfile) -> String:
	var shares := two_strike_shares(config, pitcher)
	var best := ""
	for id: String in config.pitches:
		if best == "" or float(shares[id]) > float(shares[best]) + 1e-9:
			best = id
	return best


## HUD 에 보일 글자 (틀은 balance.json batting.presentation.pitchMemo.text — %s 자리에 구종 한글 이름)
static func memo(config: BattingConfig, pitcher: BattingConfig.PitcherProfile, template: String) -> String:
	return template % config.catalog.spec(putaway_type(config, pitcher)).name_ko


## 힌트가 살아 있나 (다음 경기용으로 받아 둔 것)
static func active(player: PlayerData) -> bool:
	return player.flags.has(FLAG)


## 지금 타석에서 메모를 보일까: 힌트가 있고, 이 경기의 내 첫 타석
static func show_now(player: PlayerData, my_pa_before: int) -> bool:
	return active(player) and my_pa_before == 0


## 경기 하나가 끝나면 힌트를 쓴다 (없으면 아무 일도 없다)
static func consume(player: PlayerData) -> void:
	player.flags.erase(FLAG)


## 메모가 보이는 정도 0~1 (나타난 뒤 지난 ms). show_ms 동안 1, 이어서 fade_ms 동안 1 → 0
static func memo_alpha(elapsed_ms: float, show_ms: float, fade_ms: float) -> float:
	if elapsed_ms < 0.0:
		return 0.0
	if elapsed_ms <= show_ms:
		return 1.0
	return clampf(1.0 - (elapsed_ms - show_ms) / maxf(fade_ms, 1.0), 0.0, 1.0)
